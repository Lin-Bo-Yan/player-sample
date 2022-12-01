package tw.com.lig.sdk.player;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.ArrayBlockingQueue;

import tw.com.lig.sdk.scanner.LightID;

public class LiGSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int MAX_QUEUE_SIZE = 20;
    private final ArrayBlockingQueue<LightID> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

    private Thread workThread;
    private int windowWidth;
    private int windowHeight;
    private float[] areaLinePoints;

    public LiGSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
    }

    public LiGSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    public LiGSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    public LiGSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        // need not to implement
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopDrawing();
        // need not to implement
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        windowWidth = getWidth();
        windowHeight = getHeight();
        final int areaWidth = windowWidth / 4;
        final int areaHeight = windowHeight / 4;
        final Point shift = new Point((windowWidth - areaWidth) / 2, (windowHeight - areaHeight) / 2);
        areaLinePoints = new float[]{
                shift.x, shift.y, shift.x, shift.y + areaHeight,
                shift.x, shift.y, shift.x + areaWidth, shift.y,
                shift.x + areaWidth, shift.y + areaHeight, shift.x + areaWidth, shift.y,
                shift.x + areaWidth, shift.y + areaHeight, shift.x, shift.y + areaHeight};
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    public synchronized void startDrawing() {
        if (workThread == null) {
            workThread = new Thread(this);
            workThread.start();
        }
    }

    public synchronized void stopDrawing() {
        if (workThread != null) {
            workThread.interrupt();
            workThread = null;
        }
    }

    public boolean send(LightID lightID) {
        if (workThread != null) {
            return queue.offer(lightID);
        }
        return false;
    }

    @Override
    public void run() {

        // initialize line paint
        Paint areaLinePaint = new Paint();
        areaLinePaint.setColor(Color.WHITE);
        areaLinePaint.setStrokeWidth(3);

        Paint idTextPaint = new Paint();
        idTextPaint.setColor(Color.RED);
        idTextPaint.setTextSize(dp2px(14));

        Paint targetCyclePaint = new Paint();
        targetCyclePaint.setColor(Color.YELLOW);

        Paint readyCyclePaint = new Paint();
        readyCyclePaint.setColor(Color.GREEN);

        SurfaceHolder holder = getHolder();
        while (workThread != null && !workThread.isInterrupted()) {
            try {
                LightID lightID = queue.take();

                if (holder == null)
                    continue;

                Canvas canvas = holder.lockCanvas();
                if (canvas == null) continue;

                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawLines(areaLinePoints, areaLinePaint);

                int aimPosX = (int) (windowWidth * lightID.getCoordinateX());
                int aimPosY = (int) (windowHeight * lightID.getCoordinateY());

                // draw aim cycle
                if (lightID.isReady()) {
                    canvas.drawCircle(aimPosX, aimPosY, dp2px(20), readyCyclePaint);
                } else {
                    canvas.drawCircle(aimPosX, aimPosY, dp2px(20), targetCyclePaint);
                }

                // draw LightID, detection time and decoded time
                if (lightID.isDetected()) {
                    String id = String.valueOf(lightID.getDeviceId());
                    canvas.drawText(id, aimPosX - dp2px(11), aimPosY + dp2px(6), idTextPaint);
                }

                holder.unlockCanvasAndPost(canvas);

            } catch (InterruptedException e) {
                break;
            }
        }
        queue.clear();
    }

    private float dp2px(int dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
