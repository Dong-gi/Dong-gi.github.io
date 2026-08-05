import time
import picamera

with picamera.PiCamera() as cam:
    cam.start_preview()
    try:
        for i in range(100):
            cam.brightness = i
            time.sleep(0.2)
    finally:
        cam.stop_preview()
