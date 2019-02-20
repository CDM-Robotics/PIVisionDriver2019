package team6072.vision;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.*;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.*;
import org.opencv.core.MatOfPoint2f;

import org.opencv.imgproc.Imgproc;

import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import org.opencv.core.Mat;

import java.util.List;
import java.util.SortedMap;

import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;

public class CloseUpPipelineListener implements VisionRunner.Listener<CloseUpPipeline> {

    // The object to synchronize on to make sure the vision thread doesn't
    // write to variables the main thread is using.
    private final Object visionLock = new Object();

    // Network Table Entrys
    private NetworkTable mTbl;
    private NetworkTableEntry mX1;
    private NetworkTableEntry mY1;
    private NetworkTableEntry mX2;
    private NetworkTableEntry mY2;

    private final double TAPE_DIST_FROM_CENTER_INCHES_X = 5.65572;
    private final double TAPE_HEIGHT_IN_INCHES = 5.825352102;
    private final double CAMERA_FOV_ANGLE_X = 0.9098492;
    private final double CAMERA_FOV_ANGLE_Y = 1.028149899; // fix this bc its probably wrong
    private final int CAMERA_PIXEL_WIDTH_PIXELS = 160;
    private final int CAMERA_PIXEL_HEIGHT_PIXELS = 120;

    // Pipelines
    private CloseUpPipeline pipeline;

    // Camera Servers
    private CameraServer mCameraServer;
    private CvSource mCameraOutput;
    // Counters
    private int mCallCounter = 0;
    private int mCounter;

    private final int X_CAMERA_FOV = 640;
    private final int Y_CAMERA_FOV = 480;

    public CloseUpPipelineListener(String camName) {
        // instantiate Network Tables
        NetworkTableInstance tblInst = NetworkTableInstance.getDefault();
        mTbl = tblInst.getTable("Vision_Drive");
        NetworkTableEntry ent = mTbl.getEntry("CamName");
        ent.setString(camName);

        // Instantiate Camera Server Stuff
        mCameraServer = CameraServer.getInstance();
        mCameraOutput = mCameraServer.putVideo(camName, 640, 480);
    }


    private boolean m_inCopyPipeline = false;

    /**
     * Called when the pipeline has run. We need to grab the output from the
     * pipeline then communicate to the rest of the system over network tables
     */
    @Override
    public void copyPipelineOutputs(CloseUpPipeline pipeline) {
        synchronized (visionLock) {
            if (m_inCopyPipeline) {
                return;
            }
            m_inCopyPipeline = true;
            Mat pic = pipeline.source();
            Point pt1 = new Point(.45 * X_CAMERA_FOV, 0.0 * Y_CAMERA_FOV);
            Point pt2 = new Point(.55 * X_CAMERA_FOV, 1.0 * Y_CAMERA_FOV);
            Scalar color = new Scalar(255);
            int thickness = 3;
            Imgproc.rectangle(pic, pt1, pt2, color, thickness);
            mCameraOutput.putFrame(pic);
            m_inCopyPipeline = false;
        }
    }

}