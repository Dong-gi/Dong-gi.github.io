import io
import time
import numpy as np
import cv2
import picamera


video = cv2.VideoCapture('fire4.mp4')
fps = int(video.get(cv2.CAP_PROP_FPS))
current = 0

frames = list()
grays = list()
background = None
thresh = np.full((360, 640), 20, np.uint8)
kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
clahe = cv2.createCLAHE(clipLimit=8, tileGridSize=(8, 8))

while video.isOpened():
    _, img = video.read()
    if current == fps / 4:
        current = 0
        continue
    current = current + 1
    
    img = cv2.resize(img, (640, 360))
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    
    lower = np.array([0, 50, 100], dtype="uint8")
    upper = np.array([50, 255, 255], dtype="uint8")
    mask = cv2.inRange(hsv, lower, upper)
    mask = cv2.equalizeHist(mask)
    gray = cv2.cvtColor(cv2.bitwise_and(img, hsv, mask=mask), cv2.COLOR_BGR2GRAY)
    
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
        
    alpha = 0.95
    moving = cv2.countNonZero(mask)
    diff = cv2.absdiff(grays[2], background)
    if moving < 777:
        background = cv2.addWeighted(background, alpha, grays[2], 1-alpha, 0)
        result = img
    else:
        mask = cv2.subtract(diff, thresh)
        _, mask = cv2.threshold(mask, 0, 255, cv2.THRESH_BINARY)
        cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, mask)
        _, contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        #result = cv2.bitwise_and(img, img, mask = mask)
        result = cv2.bitwise_and(img, img)
        print('Number of contours : {}'.format(len(contours)))
        for contour in contours:
            if cv2.arcLength(contour, True) < 50:
                continue
            epsilon = 0.01*cv2.arcLength(contour, True)
            approx = cv2.approxPolyDP(contour, epsilon, True)
            #hull = cv2.convexHull(contour)
            cv2.drawContours(result, [approx], 0, (0, 0, 255), 3)
        background = grays[2]
        
    cv2.imshow('original', img)
    cv2.imshow('background', background)
    cv2.imshow('result', result)

    if cv2.waitKey(2) >= 0:
        break
