import io
import time
import picamera
import numpy as np
import cv2

with picamera.PiCamera() as cam:
    cam.resolution = (640, 480)
    img = np.empty((1, 480 * 640 * 3), dtype=np.uint8)
    frames = list()
    grays = list()
    background = None
    thresh = np.full((480, 640), 30, np.uint8)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    
    start = time.time()
    for _ in cam.capture_continuous(img[0], format='bgr', use_video_port=True):
        img = np.reshape(img, (480, 640, 3))
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        #cv2.imshow('gray', gray)
        
        frames.append(img)
        grays.append(gray)
        if len(frames) < 3:
            continue
        elif len(frames) == 3:
            background = grays[2]
        else:
            frames.remove(frames[0])
            grays.remove(grays[0])
            
        diff1 = cv2.absdiff(grays[2], grays[1])
        diff1 = cv2.subtract(diff1, thresh)
        _, diff1 = cv2.threshold(diff1, 0, 255, cv2.THRESH_BINARY)
        diff2 = cv2.absdiff(grays[2], grays[0])
        diff2 = cv2.subtract(diff2, thresh)
        _, diff2 = cv2.threshold(diff2, 0, 255, cv2.THRESH_BINARY)
        mask = cv2.bitwise_and(diff1, diff2)
        
        alpha = 0.96
        moving = cv2.countNonZero(mask)
        if moving < 1000:
            background = cv2.addWeighted(background, alpha, grays[2], 1-alpha, 0)
            result = img
        else:
            diff = cv2.absdiff(grays[2], background)
            mask = cv2.subtract(diff, thresh)
            thresh = cv2.addWeighted(thresh, alpha, diff, 1-alpha, 0)
            _, mask = cv2.threshold(mask, 0, 255, cv2.THRESH_BINARY)
            #cv2.erode(mask, kernel, mask, iterations = 1)
            cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, mask)
            _, contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            #result = cv2.bitwise_and(img, img, mask = mask)
            result = cv2.bitwise_and(img, img)
            for contour in contours:
                epsilon = 0.01*cv2.arcLength(contour, True)
                approx = cv2.approxPolyDP(contour, epsilon, True)
                #hull = cv2.convexHull(contour)
                cv2.drawContours(result, [approx], 0, (0, 0, 255), 3)
            
        cv2.imshow('original', img)
        cv2.imshow('background', background)
        #cv2.imshow('mask', mask)
        cv2.imshow('result', result)
        
        print('{:.2f}fps'.format(1/(time.time()-start)))
        start = time.time()
        if cv2.waitKey(2) >= 0:
            break

