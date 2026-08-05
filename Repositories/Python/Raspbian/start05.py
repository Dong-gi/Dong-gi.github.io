import time
import picamera

with picamera.PiCamera() as cam:
    cam.resolution = (2592, 1944)
    cam.start_preview()
    time.sleep(1)
    cam.exif_tags['IFD0.Artist'] = 'Me!'
    cam.exif_tags['IFD0.Copyright'] = 'Copyright (c) 2013 Me!'
    cam.capture('start05.jpg')
    cam.stop_preview()
