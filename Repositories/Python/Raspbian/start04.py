import time
import picamera

with picamera.PiCamera() as cam:
    cam.resolution = (800, 450)
    cam.start_preview()
    # Camera parameters
    cam.exposure_compensation = 2
    cam.exposure_mode = 'spotlight'
    cam.meter_mode = 'matrix'
    cam.image_effect = 'gpen'
    # Give the camera some time to adjust to conditions
    time.sleep(1)
    cam.capture('start04.jpg')
    cam.stop_preview()
