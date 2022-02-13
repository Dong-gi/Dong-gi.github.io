import time
import picamera

with picamera.PiCamera() as cam:
    cam.resolution = (900, 450)
    cam.start_preview()
    time.sleep(1)
    start = time.time()
    cam.capture_sequence(
        ('start07_%02d.jpg' % i for i in range(30)),
        use_video_port=True)
    print('Captured 30 images at %.2ffps' % (30 / (time.time() - start)))
    cam.stop_preview()
