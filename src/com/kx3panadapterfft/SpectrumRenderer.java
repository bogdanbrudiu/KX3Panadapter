package com.kx3panadapterfft;

import java.text.DecimalFormat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

public class SpectrumRenderer {

	Bitmap waterfall;
	int[] waterfallPixels;
	float[] spectrumPoints;

	private final int waterfallHigh = (int) (10 * Math.log10(1));
	private final int waterfallLow = (int) (10 * Math.log10(0.000000001f));
	private final int spectrumHigh = (int) (10 * Math.log10(1));
	private final int spectrumLow = (int) (10 * Math.log10(0.000000001f));

	static int colorLowR = 128; // black
	static int colorLowG = 128;
	static int colorLowB = 128;

	static int colorMidR = 255; // red
	static int colorMidG = 0;
	static int colorMidB = 0;

	static int colorHighR = 255; // yellow
	static int colorHighG = 255;
	static int colorHighB = 0;
	static float border = 5;
	static float tick = 3;
	private final int rate;

	public SpectrumRenderer(int rate) {
		super();
		this.rate = rate;

	}

	public void render(Canvas canvas, double[] data, double freqA) {
		float min = 10000;
		float max = -10000;

		int scaleStep=2000;
		float spectrumWidth = (canvas.getWidth() - 2 * (border)) - tick;
		float spectrumHeight = ((canvas.getHeight() - 2 * (border)) / 3) - tick;
		float waterfallWidth = (canvas.getWidth() - 2 * (border)) - tick;
		float waterfallHeight = (2 * (canvas.getHeight() - 2 * border) / 3) - tick;

		float spectrumTopX = border + tick / 2;
		float spectrumTopY = border + tick / 2;
		float spectrumBottomX = border + tick / 2 + spectrumWidth;
		float spectrumBottomY = border + tick / 2 + spectrumHeight;

		float waterfallTopX = border + tick / 2;
		float waterfallTopY = border + tick / 2 + tick + spectrumHeight;
		// float waterfallBottomX=border+tick/2+waterfallWidth;
		// float waterfallBottomY=border+tick/2+tick+spectrumHeight
		// +waterfallHeight;

		if (waterfall == null)
			waterfall = Bitmap.createBitmap((int) waterfallWidth, (int) waterfallHeight, Bitmap.Config.ARGB_8888);
		if (waterfallPixels == null)
			waterfallPixels = new int[(int) waterfallWidth * (int) waterfallHeight];
		if (spectrumPoints == null)
			spectrumPoints = new float[(int) spectrumWidth * 2];

		Paint textpaint = new Paint(); 

		textpaint.setColor(Color.YELLOW); 
		textpaint.setTextSize(16); 
		textpaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		
		Paint myPaint = new Paint();
		// clear spectrum
		myPaint.setColor(android.graphics.Color.BLACK);
		myPaint.setStyle(Paint.Style.FILL);
		canvas.drawRect(spectrumTopX, spectrumTopY, spectrumBottomX, spectrumBottomY, myPaint);
		canvas.drawRect(0, 0, border, spectrumWidth, myPaint);
		

		// plot border
		myPaint.setColor(Color.WHITE);
		myPaint.setStyle(Paint.Style.STROKE);
		myPaint.setStrokeWidth(tick);

		canvas.drawRect(border, border, canvas.getWidth() - border, canvas.getHeight() - border, myPaint);

		
		float freqdelta=(float) (freqA%scaleStep);
		//if(freqdelta>scaleStep/2){
		//	freqdelta=(float) (freqdelta-scaleStep);
		//}
		freqdelta=spectrumWidth * freqdelta/rate;
		float delta =  (float) (spectrumWidth * scaleStep / rate); // (2khz wide)

		int myRemainingSpectrumWidth = 0;
		while (myRemainingSpectrumWidth+freqdelta <= spectrumWidth / 2) {
			float xneg=border + tick / 2 + spectrumWidth / 2 - myRemainingSpectrumWidth+freqdelta;
			float xpoz=border + tick / 2 + spectrumWidth / 2 + myRemainingSpectrumWidth+freqdelta;
					
			canvas.drawLine(xneg, 0, xneg, border, myPaint);
			canvas.drawLine(xneg, canvas.getHeight() - border, xneg, canvas.getHeight(), myPaint);
			if(myRemainingSpectrumWidth!=0){ //if its the first one then the center is already done
				canvas.drawLine(xpoz, 0, xpoz, border, myPaint);
				canvas.drawLine(xpoz, canvas.getHeight() - border, xpoz, canvas.getHeight(), myPaint);
			}
			myRemainingSpectrumWidth += delta;
		}

		canvas.drawLine(border, border + tick + spectrumHeight, border + tick + spectrumWidth, border + tick + spectrumHeight, myPaint);

		// plot the spectrum levels
		myPaint.setStrokeWidth(1);
		int V = spectrumHigh - spectrumLow;
		int numSteps = V / 20;
		for (int i = 1; i < numSteps; i++) {
			int num = spectrumHigh - i * 20;
			int y = (int) (border + tick / 2) + (int) Math.floor((spectrumHigh - num) * spectrumHeight / V);

			Rect bounds = new Rect();
			textpaint.getTextBounds(Integer.toString(num), 0, Integer.toString(num).length(), bounds);
			canvas.drawText(Integer.toString(num), border + tick + 2, y + bounds.height() / 2, textpaint);

			myPaint.setColor(Color.WHITE);
			myPaint.setPathEffect(new DashPathEffect(new float[] { 3, 6 }, 0));
			canvas.drawLine(border + tick + bounds.width() + 6, y, canvas.getWidth() - border - tick, y, myPaint);
			myPaint.setPathEffect(null);

		}


		canvas.drawText(Integer.toString(rate)+" bw", border + tick + 40, border + tick + 15, textpaint);
		canvas.drawText(Integer.toString(data.length / 2)+" bins", border + tick + 120, border + tick + 15, textpaint);
		
		Rect freqBounds = new Rect();
		 DecimalFormat myFormatter = new DecimalFormat("#.###");
	      String output = myFormatter.format(freqA);
		myPaint.getTextBounds(output, 0, output.length(), freqBounds);
		canvas.drawText(output, spectrumWidth/2-freqBounds.width()/2, border + tick + 15, textpaint);
		
		
		// scroll down
		waterfall.getPixels(waterfallPixels, 0, (int) waterfallWidth, 0, 0, (int) waterfallWidth, (int) waterfallHeight - 1);
		waterfall.setPixels(waterfallPixels, 0, (int) waterfallWidth, 0, 1, (int) waterfallWidth, (int) waterfallHeight - 1);

		int mDivisions = (int) spectrumWidth;
		float step;

		step = (float) (data.length / 2) / mDivisions;

		for (int i = 0; i < mDivisions; i++) {
			double rfk;
			double ifk;

			rfk = data[((int) (step * i)) * 2];
			ifk = data[((int) (step * i)) * 2 + 1];

			double power = (rfk * rfk + ifk * ifk);

			float dbValue = power > 0 ? (int) (10 * Math.log10(power / (1))) : spectrumLow;
			if (min > dbValue) {
				min = dbValue;
			}
			if (max < dbValue) {
				max = dbValue;
			}

			float range = spectrumHigh - spectrumLow;
			float offset = dbValue - spectrumLow;
			float percent = Math.abs(offset) / range;

			spectrumPoints[i * 2] = spectrumTopX + i;
			spectrumPoints[i * 2 + 1] = spectrumTopY + spectrumHeight - spectrumHeight * percent;
			waterfall.setPixel(i, 0, calculatePixel(dbValue));
		}

		myPaint.setColor(Color.WHITE);
		canvas.drawLines(spectrumPoints, myPaint);
		canvas.drawBitmap(waterfall, waterfallTopX, waterfallTopY, null);

	}

	private int calculatePixel(float sample) {

		int R, G, B;

		float range = waterfallHigh - waterfallLow;
		float offset = sample - waterfallLow;
		float percent = offset / range;
		if (percent < (2.0f / 9.0f)) {
			float local_percent = percent / (2.0f / 9.0f);
			R = (int) ((1.0f - local_percent) * colorLowR);
			G = (int) ((1.0f - local_percent) * colorLowG);
			B = (int) (colorLowB + local_percent * (255 - colorLowB));
		} else if (percent < (3.0f / 9.0f)) {
			float local_percent = (percent - 2.0f / 9.0f) / (1.0f / 9.0f);
			R = 0;
			G = (int) (local_percent * 255);
			B = 255;
		} else if (percent < (4.0f / 9.0f)) {
			float local_percent = (percent - 3.0f / 9.0f) / (1.0f / 9.0f);
			R = 0;
			G = 255;
			B = (int) ((1.0f - local_percent) * 255);
		} else if (percent < (5.0f / 9.0f)) {
			float local_percent = (percent - 4.0f / 9.0f) / (1.0f / 9.0f);
			R = (int) (local_percent * 255);
			G = 255;
			B = 0;
		} else if (percent < (7.0f / 9.0f)) {
			float local_percent = (percent - 5.0f / 9.0f) / (2.0f / 9.0f);
			R = 255;
			G = (int) ((1.0f - local_percent) * 255);
			B = 0;
		} else if (percent < (8.0f / 9.0f)) {
			float local_percent = (percent - 7.0f / 9.0f) / (1.0f / 9.0f);
			R = 255;
			G = 0;
			B = (int) (local_percent * 255);
		} else {
			float local_percent = (percent - 8.0f / 9.0f) / (1.0f / 9.0f);
			R = (int) ((0.75f + 0.25f * (1.0f - local_percent)) * 255.0f);
			G = (int) (local_percent * 255.0f * 0.5f);
			B = 255;
		}
		return Color.rgb(R, G, B);
	}

}
