
/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package team6072.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.*;
import edu.wpi.first.vision.VisionPipeline;
import edu.wpi.first.vision.VisionThread;

import org.opencv.core.Mat;

/*
   JSON format:
   {
       "team": <team number>,
       "ntmode": <"client" or "server", "client" if unspecified>
       "cameras": [
           {
               "name": <camera name>
               "path": <path, e.g. "/dev/video0">
               "pixel format": <"MJPEG", "YUYV", etc>   // optional
               "width": <video mode width>              // optional
               "height": <video mode height>            // optional
               "fps": <video mode fps>                  // optional
               "brightness": <percentage brightness>    // optional
               "white balance": <"auto", "hold", value> // optional
               "exposure": <"auto", "hold", value>      // optional
               "properties": [                          // optional
                   {
                       "name": <property name>
                       "value": <property value>
                   }
               ]
           }
       ]
   }
 */

public final class Main {

    private static String CONFIGFILE = "/boot/frc.json";

    @SuppressWarnings("MemberName")
    public static class CameraConfig {
        public String name;
        public String path;
        public JsonObject config;
    }

    public static int m_team;

    private static boolean m_useCam0 = true;

    public static List<CameraConfig> m_cameraConfigs = new ArrayList<>();

    private static ArrayList<VideoSource> m_cameras;

    private static VisionThread m_visionThread;

    private static VideoSource m_cam0;
    private static VideoSource m_cam1;

    private static CloseUpPipelineListener m_listener0;
    private static CloseUpPipelineListener m_listener1;

    private static VisionThread m_visionThread0;
    private static VisionThread m_visionThread1;

    // private constructor
    private Main() {
    }

    /**
     * Main.
     */
    public static void main(String... args) {
        if (args.length > 0) {
            CONFIGFILE = args[0];
        }

        // read configuration
        if (!readConfig()) {
            return;
        }

        // start NetworkTables
        NetworkTableInstance tblInst = NetworkTableInstance.getDefault();
        m_team = 6072;
        System.out.println("Setting up NetworkTables client for team " + m_team);
        tblInst.startClientTeam(m_team);
        NetworkTable tbl = tblInst.getTable("Vision_Drive");
        NetworkTableEntry ent = tbl.getEntry("PI Name");
        ent.setString("Drive PI");
        NetworkTableEntry entUseCam0 = tbl.getEntry("UseCam0");
        entUseCam0.setBoolean(m_useCam0);
        //tbl.addEntryListener("UseCam0", Main::UseCam0_Listener, EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

        // define cameras, start pipeline on camera 0
        // m_cameras = new ArrayList<VideoSource>();
        // for (CameraConfig cameraConfig : m_cameraConfigs) {
        //     System.out.println("Starting camera: " + cameraConfig.name + "  path: " + cameraConfig.path);
        //     VideoSource cam = makeCamera(cameraConfig);
        //     m_cameras.add(cam);
        // }
        m_cam0 = makeCamera(m_cameraConfigs.get(0));
        m_cam1 = makeCamera(m_cameraConfigs.get(1));
        m_listener0 = new CloseUpPipelineListener(m_cam0.getName());
        m_listener0.setEnabled(true);
        m_listener1 = new CloseUpPipelineListener(m_cam1.getName());
        m_listener1.setEnabled(true);

        m_visionThread0 = new VisionThread(m_cam0, new CloseUpPipeline(), m_listener0);
        m_visionThread0.start();

        m_visionThread1 = new VisionThread(m_cam1, new CloseUpPipeline(), m_listener1);
        m_visionThread1.start();

        // loop forever
        for (;;) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    // implement TableEntry Listener
    public static void UseCam0_Listener(NetworkTable table, String key, NetworkTableEntry entry, NetworkTableValue value, int flags) {
        m_useCam0 = !m_useCam0;
        int camNbr = 0;
        if (m_useCam0) {
            m_listener0.setEnabled(true);
            m_listener1.setEnabled(false);
        }
        else {
            m_listener0.setEnabled(false);
            m_listener1.setEnabled(true);          
        }
        System.out.println(String.format("useCam:  key %s  val: %b  camNbr: %d", key, value.getBoolean(), camNbr));
    }



    /**
     * Create and configure a camera from the config file.
     */
    public static UsbCamera makeCamera(CameraConfig config) {
        System.out.println("Starting camera '" + config.name + "' on " + config.path);
        UsbCamera camera = new UsbCamera(config.name, config.path);
        //VideoSource camera = CameraServer.getInstance().startAutomaticCapture(config.name, config.path);
        Gson gson = new GsonBuilder().create();
        camera.setConfigJson(gson.toJson(config.config));
        return camera;
    }
    

    /**
     * Read configuration file. Return FALSE if fail
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public static boolean readConfig() {
        // parse file
        JsonElement top;
        try {
            top = new JsonParser().parse(Files.newBufferedReader(Paths.get(CONFIGFILE)));
        } catch (IOException ex) {
            System.err.println("could not open '" + CONFIGFILE + "': " + ex);
            return false;
        }

        // top level must be an object
        if (!top.isJsonObject()) {
            parseError("must be JSON object");
            return false;
        }
        JsonObject obj = top.getAsJsonObject();

        // team number
        JsonElement teamElement = obj.get("team");
        if (teamElement == null) {
            parseError("could not read team number");
            return false;
        }
        m_team = teamElement.getAsInt();

        // ntmode (optional)
        // if (obj.has("ntmode")) {
        //     String str = obj.get("ntmode").getAsString();
        //     if ("client".equalsIgnoreCase(str)) {
        //         server = false;
        //     } else if ("server".equalsIgnoreCase(str)) {
        //         server = true;
        //     } else {
        //         parseError("could not understand ntmode value '" + str + "'");
        //     }
        // }

        // cameras
        JsonElement camerasElement = obj.get("cameras");
        if (camerasElement == null) {
            parseError("could not read cameras");
            return false;
        }
        JsonArray cameras = camerasElement.getAsJsonArray();
        for (JsonElement camera : cameras) {
            if (!readCameraConfig(camera.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Report parse error.
     */
    public static void parseError(String str) {
        System.err.println("config error in '" + CONFIGFILE + "': " + str);
    }

    /**
     * Read single camera configuration.
     */
    public static boolean readCameraConfig(JsonObject config) {
        CameraConfig cam = new CameraConfig();

        // name
        JsonElement nameElement = config.get("name");
        if (nameElement == null) {
            parseError("could not read camera name");
            return false;
        }
        cam.name = nameElement.getAsString();

        // path
        JsonElement pathElement = config.get("path");
        if (pathElement == null) {
            parseError("camera '" + cam.name + "': could not read path");
            return false;
        }
        cam.path = pathElement.getAsString();

        cam.config = config;

        m_cameraConfigs.add(cam);
        return true;
    }



}
