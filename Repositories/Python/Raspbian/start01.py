import time
import picamera

with picamera.PiCamera() as cam:
    cam.start_preview()
    time.sleep(10)
    cam.stop_preview()
