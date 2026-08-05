import io
import time
import numpy as np
import cv2
import picamera


video = cv2.VideoCapture('fire1.mp4')
_, img = video.read()
frames = list()
grays = list()
background = None
thresh = np.full((len(img), len(img[0])), 30, np.uint8)
kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))

while video.isOpened():
    _, img = video.read()
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    _, gray = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

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
        print('Number of contours : {}'.format(len(contours)))
        for contour in contours:
            if cv2.arcLength(contour, True) < 30:
                continue
            epsilon = 0.01*cv2.arcLength(contour, True)
            approx = cv2.approxPolyDP(contour, epsilon, True)
            #hull = cv2.convexHull(contour)
            cv2.drawContours(result, [approx], 0, (0, 0, 255), 3)
        #background = cv2.addWeighted(background, 1-alpha, grays[2], alpha, 0)

    show_ori = cv2.resize(img, (640, 360))
    show_back = cv2.resize(background, (640, 360))
    show_res = cv2.resize(result, (640, 360))
    cv2.imshow('original', show_ori)
    cv2.imshow('background', show_back)
    cv2.imshow('result', show_res)

    if cv2.waitKey(2) >= 0:
        break

