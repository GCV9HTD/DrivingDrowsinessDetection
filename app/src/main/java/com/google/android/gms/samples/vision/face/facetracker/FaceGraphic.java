/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;
import android.net.Uri;
import android.media.RingtoneManager;
import android.media.MediaPlayer;
import android.content.Context;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int COLOR_CHOICES[] = {
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.MAGENTA,
            Color.RED,
            Color.WHITE,
            Color.YELLOW
    };
    private static int mCurrentColorIndex = 0;

    private Paint mFacePositionPaint;
    private Paint mIdPaint;
    private Paint mBoxPaint;

    private volatile Face mFace;
    private int mFaceId;
    private float mFaceHappiness;


    private final float OPEN_THRESHOLD = 0.85f;
    private final float CLOSE_THRESHOLD = 0.15f;
    private int state = 0;

    private Context activityContext;
    private MediaPlayer mediaPlayer;
    private int time = 0;
    private boolean eyesOpen = true;
    private int sleepTimer = 0;
    private boolean drowsyBlink;
    private int blinkCounter = 0;
    private int timeBetweenBlinks = 0;
    private boolean firstBlink = true;
    private boolean alerted;

    FaceGraphic(GraphicOverlay overlay, Context context) {
        super(overlay);

        mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

        activityContext = context;
    }

    void setId(int id) {
        mFaceId = id;
    }


    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);
        canvas.drawText("id: " + mFaceId, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);
        canvas.drawText("happiness: " + String.format("%.2f", face.getIsSmilingProbability()), x - ID_X_OFFSET, y - ID_Y_OFFSET, mIdPaint);
        canvas.drawText("right eye: " + String.format("%.2f", face.getIsRightEyeOpenProbability()), x + ID_X_OFFSET * 2, y + ID_Y_OFFSET * 2, mIdPaint);
        canvas.drawText("left eye: " + String.format("%.2f", face.getIsLeftEyeOpenProbability()), x - ID_X_OFFSET * 2, y - ID_Y_OFFSET * 2, mIdPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        detectBlinks(face, canvas, x, y);
        sleepingWarning(face);
    }

    public void detectBlinks(Face face, Canvas canvas, float x, float y) {
        float left = face.getIsLeftEyeOpenProbability();
        float right = face.getIsRightEyeOpenProbability();
        if ((left == Face.UNCOMPUTED_PROBABILITY) || (right == Face.UNCOMPUTED_PROBABILITY)) {
            return;
        }

        switch (state) {
            case 0:
                if ((left > OPEN_THRESHOLD) && (right > OPEN_THRESHOLD)) {
                    // BOTH EYES INITIALLY OPEN
                    state = 1;
                }
                break;

            case 1:
                if ((left < CLOSE_THRESHOLD) && (right < CLOSE_THRESHOLD)) {
                    // BOTH EYES CLOSED
                    state = 2;

                    time = (int) System.currentTimeMillis();
                }
                break;

            case 2:
                if ((left > OPEN_THRESHOLD) && (right > OPEN_THRESHOLD)) {
                    // BOTH EYES ARE OPEN AGAIN
                    state = 0;

                    time = (int) System.currentTimeMillis() - time;

                    int cutTime = time / (int) Math.pow(10, 2);
                    String stringTime = (cutTime / 10) + "." + (cutTime % 10);
                    Log.d("blinkTime", stringTime);
                    canvas.drawText(stringTime, x + ID_X_OFFSET * 4, y - ID_Y_OFFSET * 5, mIdPaint); // if time > some threshold, then they are asleep

                    if (time >=1000 && (int)System.currentTimeMillis() < timeBetweenBlinks+minuteTimer(1) && !firstBlink){
                        blinkCounter += 1;
                        Log.d("blink counter","1" + blinkCounter);
                    }
                    else if (time >= 1000 && firstBlink){
                        blinkCounter +=1;
                        firstBlink = false;
                        timeBetweenBlinks = (int)System.currentTimeMillis();
                        Log.d("blink counter","2" + blinkCounter);
                    }

                    else if (time >=1000 && (int)System.currentTimeMillis() > timeBetweenBlinks+minuteTimer(1)){
                        blinkCounter = 0;
                        firstBlink = true;
                        Log.d("blink counter","3" + blinkCounter);
                    }

                    if (time >= 1000 && blinkCounter > 2 ){
                        Log.d("blink counter","4" + blinkCounter);
                        //alarmSound();
                        if (!alerted) {
                            FaceTrackerActivity.getInstance().restAlert();
                            alerted = true;
                        }
                    }
                }
                break;
        }
    }

    public void sleepingWarning(Face face) {
        float left = face.getIsLeftEyeOpenProbability();
        float right = face.getIsRightEyeOpenProbability();

        if ((left < CLOSE_THRESHOLD) && (right < CLOSE_THRESHOLD) && eyesOpen) {
            sleepTimer = (int) System.currentTimeMillis();
            drowsyBlink = false;
            eyesOpen = false;
        }
        if ((int) System.currentTimeMillis() > sleepTimer + 1000 && !eyesOpen && !drowsyBlink) {
            Log.d("drowsy", Integer.toString((int) System.currentTimeMillis() - sleepTimer));
            drowsyBlink = true;
            alarmSound();
        }
        if ((int) System.currentTimeMillis() > sleepTimer + 2000 && !eyesOpen) {
            Log.d("sleepy", Integer.toString((int) System.currentTimeMillis() - sleepTimer));
            alarmSound();
            if (!alerted) {
                FaceTrackerActivity.getInstance().restAlert();
                alerted = true;
            }
        }
        if ((left > OPEN_THRESHOLD) && (right > OPEN_THRESHOLD) && !eyesOpen) {
            eyesOpen = true;
        }
    }

    public void alarmSound() {
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mediaPlayer = MediaPlayer.create(activityContext, alert);
        mediaPlayer.start();
    }

    public int minuteTimer(int mins){
        int r_v = 0;
        for (int i = 0; i < mins; i++) {
            r_v += 60000;
        }
        return r_v;
    }



}