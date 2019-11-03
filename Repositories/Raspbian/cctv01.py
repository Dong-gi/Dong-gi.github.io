import io
import time
import picamera
import numpy as np
import cv2

faceCascade = cv2.CascadeClassifier('haarcascade_frontalface_alt.xml')
eyeCascade = cv2.CascadeClassifier('haarcascade_eye.xml')
#for e in dir(faceCascade):
#    print(e)
#help(faceCascade.detectMultiScale)

with picamera.PiCamera() as cam:
    cam.resolution = (1920, 1080)
    stream = io.BytesIO()
    for _ in cam.capture_continuous(stream, format='jpeg', quality=100, use_video_port=True):
        data = np.fromstring(stream.getvalue(), dtype=np.uint8)
        img = cv2.imdecode(data, 1)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        stream.seek(0)
        #cv2.imshow('gray', gray)
        found = 0
        start = time.time()
        
        faces = faceCascade.detectMultiScale(gray, 1.3, 3)
        for (x, y, w, h) in faces:
            img = cv2.rectangle(img, (x, y), (x+w, y+h), (255, 0, 0), 2)
            roiGray = gray[y:y+h, x:x+w]
            roiColor = img[y:y+h, x:x+w]
            eyes = eyeCascade.detectMultiScale(roiGray, 1.2, 4)
            for (ex, ey, ew, eh) in eyes:
                #print(ew, eh)
                cv2.rectangle(roiColor, (ex, ey), (ex+ew, ey+eh), (0, 255, 0), 2)
                found = found + 1
        
        if found > 0:
            print('{}eye(s) found : {:.2f}s'.format(found, time.time()-start))
        cv2.imshow('img', img)
        if cv2.waitKey(2) >= 0:
            break
