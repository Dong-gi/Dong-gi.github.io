import time
import picamera

with picamera.PiCamera() as cam:
    cam.resolution = (640, 480)
    cam.start_preview()
    cam.start_recording('start03.h264')
    cam.wait_recording(10) # wait_recording() checks for errors while the recording is running
    cam.stop_recording()
    cam.stop_preview()
