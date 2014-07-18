package com.kx3panadapterfft;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public class VisualizerView extends View {
	@SuppressWarnings("unused")
	private static final String TAG = "VisualizerView";
	private Bitmap mCanvasBitmap;
	private Canvas mCanvas;
	private double[] fftData;
	private double freqA=14009600;
	private double freqB;
	private int blockSize;
	private int rate;
	private SpectrumRenderer spectrumRenderer;

	public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.com_kx3panadapterfft_VisualizerView, 0, 0);
		try {
			blockSize = ta.getInteger(R.styleable.com_kx3panadapterfft_VisualizerView_blockSize, 256);
			rate = ta.getInteger(R.styleable.com_kx3panadapterfft_VisualizerView_frequency, 8000);
		} finally {
			ta.recycle();
		}
		init(blockSize, rate);
	}

	public void init(int blockSize, int rate) {
		this.blockSize = blockSize;
		this.rate = rate;
		fftData = new double[blockSize];

		spectrumRenderer = new SpectrumRenderer(rate);
	}

	public VisualizerView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public VisualizerView(Context context) {
		this(context, null, 0);
	}

	public void updateVisualizer(double[] fftData) {
		if(SoundRecordAndAnalysisActivity.STEREO){
		for (int i = 0; i < fftData.length; i++) {
			if (i < fftData.length / 2) {
				this.fftData[i] = Double.isNaN(fftData[i + (fftData.length / 2)])?1:fftData[i + (fftData.length / 2)];
			} else {
				this.fftData[i] = Double.isNaN(fftData[i - (fftData.length / 2)])?1:fftData[i - (fftData.length / 2)];
			}
		}
		}else{
			this.fftData=fftData;
		}

		invalidate();
	}
	public void updateVisualizer(double freqA, double freqB) {

		this.freqA=freqA;
		this.freqB=freqB;

		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (mCanvasBitmap == null) {
			mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
		}
		if (mCanvas == null) {
			mCanvas = new Canvas(mCanvasBitmap);
		}

		spectrumRenderer.render(mCanvas, fftData,freqA);
		canvas.drawBitmap(mCanvasBitmap, 0, 0, null);

	}
}