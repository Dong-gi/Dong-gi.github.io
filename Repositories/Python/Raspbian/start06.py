import time
import picamera

with picamera.PiCamera() as cam:
    cam.resolution = (1366, 768)
    cam.start_preview()
    time.sleep(1)
    for i, filename in enumerate(cam.capture_continuous('start06_{counter:02d}.jpg')):
        print('Captured image %s' % filename)
        if i == 10:
            break
        time.sleep(0.1)
    cam.stop_preview()
