package team6072.vision;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.*;


import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.*;
import org.opencv.core.Point;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;


public class CloseUpPipelineListener implements VisionRunner.Listener<CloseUpPipeline> {

    // The object to synchronize on to make sure the vision thread doesn't
    // write to variables the main thread is using.
    private final Object visionLock = new Object();

    // Network Table Entrys
    private NetworkTable mTbl;

    // Camera Servers
    private CameraServer mCameraServer;
    private CvSource mCameraOutput;

    private final int X_CAMERA_FOV = 320;
    private final int Y_CAMERA_FOV = 240;


    public CloseUpPipelineListener(String camName) {
        // instantiate Network Tables
        NetworkTableInstance tblInst = NetworkTableInstance.getDefault();
        mTbl = tblInst.getTable("Vision_Drive");
        NetworkTableEntry ent = mTbl.getEntry("CamName");
        ent.setString(camName);

        // Instantiate Camera Server Stuff
        mCameraServer = CameraServer.getInstance();
        mCameraOutput = mCameraServer.putVideo(camName, 320, 240);
    }


    private boolean m_inCopyPipeline = false;

    private boolean m_enabled = false;

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }

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
            if (m_enabled) {
               // mCameraOutput.putFrame(pipeline.source());
                m_inCopyPipeline = true;
                Mat pic = pipeline.source();
                Point pt1 = new Point(.45 * X_CAMERA_FOV, 0.0 * Y_CAMERA_FOV);
                Point pt2 = new Point(.55 * X_CAMERA_FOV, 1.0 * Y_CAMERA_FOV);
                Scalar color = new Scalar(255);
                int thickness = 3;
                Imgproc.rectangle(pic, pt1, pt2, color, thickness);
                mCameraOutput.putFrame(pic);
            }
            m_inCopyPipeline = false;
        }
    }

}