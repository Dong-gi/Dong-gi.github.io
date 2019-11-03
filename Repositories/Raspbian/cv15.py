import numpy as np
import cv2


img = cv2.imread('start05-2.jpg')
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
gray = cv2.Canny(gray, 50, 150)
cv2.imshow('gray', gray)
_, contours, _ = cv2.findContours(gray, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

for contour in contours:
    if cv2.arcLength(contour, True) < 150:
        continue
    moments = cv2.moments(contour)
    cx = int(moments['m10']/moments['m00'])
    cy = int(moments['m01']/moments['m00'])
    print('center : ({}, {})'.format(cx, cy))
    print('perimeter : {}'.format(cv2.arcLength(contour, True)))
    print('area : {}'.format(cv2.contourArea(contour)))
    cv2.drawContours(img, [contour], 0, (0, 255, 255), 3)
    img = cv2.circle(img, (cx, cy), 5, (0, 0, 255), -1)
    cv2.imshow('img', img)
    cv2.waitKey(100)
cv2.waitKey(0)
    
