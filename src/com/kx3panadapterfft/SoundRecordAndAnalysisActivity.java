package com.kx3panadapterfft;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;
import ca.uol.aig.fftpack.ComplexDoubleFFT;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.kx3panadapterfft.wav.WavFile;

public class SoundRecordAndAnalysisActivity extends Activity {
	private static final String TAG = "SoundRecordAndAnalysisActivity";
	public static final boolean MIC = false;
	public static final boolean PLAYBACK = false;
	public static final boolean STEREO = true;
	RecordAudio recordTask;
	ImageView imageViewDisplaySectrum;
	Bitmap bitmapDisplaySpectrum;
	Canvas canvasDisplaySpectrum;

	Paint paintSpectrumDisplay;
	private long backPressedTime = 0; // used by onBackPressed()
	private final int rate = 44100;
	private final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
	private final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	private final int blockSize = 1024;
	private VisualizerView visualizerView;
	
	private static UsbSerialDriver sDriver;
	private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	 private SerialInputOutputManager mSerialIoManager;

	    private final SerialInputOutputManager.Listener mListener =
	            new SerialInputOutputManager.Listener() {

	        @Override
	        public void onRunError(Exception e) {
	            Log.d(TAG, "Runner stopped.");
	        }

	        @Override
	        public void onNewData(final byte[] data) {
	        	visualizerView.updateVisualizer(0d,0d);
	        }
	    };
	
	 /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param driver
     */
    static void show(Context context, UsbSerialDriver driver) {
    	SoundRecordAndAnalysisActivity.sDriver = driver;
        final Intent intent = new Intent(context, SoundRecordAndAnalysisActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	
		visualizerView = (VisualizerView) findViewById(R.id.visualizerView);
		visualizerView.init(blockSize, rate);
	}

	@Override
	public void onBackPressed() {

		super.onBackPressed();

		long t = System.currentTimeMillis();
		if (t - backPressedTime > 2000) { // 2 secs
			backPressedTime = t;
			Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
		} else { // this guy is serious
			// clean up
			if (recordTask != null) {
				recordTask.cancel(true);
			}
			super.onBackPressed(); // bye
		}

	}

	@Override
	public void onStart() {
		super.onStart();
		if (recordTask == null) {

			recordTask = new RecordAudio();
			recordTask.execute();

		}
	}
	
	
	@Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sDriver != null) {
            try {
                sDriver.close();
            } catch (IOException e) {
                // Ignore.
            }
            sDriver = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, sDriver=" + sDriver);
        if (sDriver == null) {
            
        } else {
            try {
                sDriver.open();
                sDriver.setBaudRate(38400);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
             
                try {
                    sDriver.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sDriver = null;
                return;
            }
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sDriver != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sDriver, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }
	

	private class RecordAudio extends AsyncTask<Void, double[], Void> {
		private static final String TAG = "RecordAudio";

		private final ComplexDoubleFFT complexTransformer;
		

		public RecordAudio() {
			complexTransformer = new ComplexDoubleFFT(blockSize / 2);
			
		}

		@SuppressLint("SdCardPath") @Override
		protected Void doInBackground(Void... params) {

			if (isCancelled()) {
				return null;
			}
			AudioTrack track;
			if (PLAYBACK) {
				track = new AudioTrack(AudioManager.STREAM_MUSIC, rate, channelConfiguration, audioEncoding, AudioTrack.getMinBufferSize(rate,
						channelConfiguration, audioEncoding), AudioTrack.MODE_STREAM);

			}
			AudioRecord audioRecord;
			WavFile wavFile;

			if (MIC) {
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfiguration, audioEncoding,
						AudioRecord.getMinBufferSize(rate, channelConfiguration, audioEncoding));
			} else {

				// File file = new File("/storage/sdcard1/sdr2.wav");
				File file = new File("/mnt/sdcard/sdr2_.wav");

				try {
					wavFile = WavFile.openWavFile(file);

					// Display information about the wav file
					wavFile.display();

				} catch (Exception e) {
					Log.e(TAG, e.toString());
					return null;
				}
			}
			short[] playBuffer;

			short[] buffer;
			double[] doubleBuffer;
			if (STEREO) {
				if (PLAYBACK) {
					playBuffer = new short[blockSize * 10];
				}
				buffer = new short[blockSize];
				doubleBuffer = new double[blockSize];
			}

			double[] toTransform = new double[doubleBuffer.length];

			try {
				if (MIC) {
					audioRecord.startRecording();
				}
				if (PLAYBACK) {
					track.play();
				}
			} catch (IllegalStateException e) {
				Log.e(TAG, e.toString());
			}
			int idx = 0;
			while (!isCancelled()) {
				int framesRead;
				if (MIC) {
					framesRead = audioRecord.read(buffer, 0, blockSize);
					framesRead = framesRead / 2;
					doubleBuffer = shortArrayToDoubleArray(buffer);
				} else {
					try {

						framesRead = wavFile.readFrames(doubleBuffer, blockSize / 2);

					} catch (Exception e) {
						Log.e(TAG, e.toString());
						try {
							wavFile.close();
						} catch (Exception ex) {
							Log.e(TAG, ex.toString());
						}

						return null;
					}
				}
				if (PLAYBACK) {
					if (idx == 9) {
						track.write(playBuffer, 0, playBuffer.length);
					} else {
						idx++;
						idx = idx % 10;
						for (int i = 0; i < buffer.length; i++) {
							playBuffer[buffer.length * idx + i] = buffer[i];
						}
					}
				}

				doubleBuffer = applyHanningWindow(doubleBuffer);
				// double[] doubleBuffer = shortArrayToDoubleArray(buffer);

				double[] doubleBufferLeft = new double[doubleBuffer.length / 2];
				double[] doubleBufferRight = new double[doubleBuffer.length / 2];

				// double[] doubleBuffer = shortArrayToDoubleArray(buffer);
				if (STEREO) {
					for (int i = 0; i < doubleBuffer.length; i++) {
						if (i % 2 == 0) {
							doubleBufferLeft[i / 2] = doubleBuffer[i];
						} else {
							doubleBufferRight[i / 2] = doubleBuffer[i];
						}
					}
				}

				for (int i = 0; i < Math.min(blockSize, framesRead); i++) {
					toTransform[i * 2] = doubleBufferLeft[i];// / 32768.0;
					toTransform[i * 2 + 1] = doubleBufferRight[i];// / 32768.0;
				}

				complexTransformer.ft(toTransform);

				publishProgress(toTransform);

			}

			try {
				if (MIC) {
					audioRecord.stop();
				} else {
					wavFile.close();
				}
				if (PLAYBACK) {
					track.stop();
				}

			} catch (Exception e) {
				Log.e(TAG, e.toString());
			}

			return null;

		}

		

		private double[] shortArrayToDoubleArray(short[] data) {
			double[] result = new double[data.length];

			for (int j = 0; j < data.length; j++) {
				result[j] = data[j];
			}
			return result;
		}

		private double[] applyHanningWindow(double[] data) {
			return applyHanningWindow(data, 0, data.length);
		}

		private double[] applyHanningWindow(double[] signal_in, int pos, int size) {
			for (int i = pos; i < pos + size; i++) {
				int j = i - pos; // j = index into Hann window function
				signal_in[i] = signal_in[i] * 0.5 * (1.0 - Math.cos(2.0 * Math.PI * j / size));
			}
			return signal_in;
		}

		@Override
		protected void onProgressUpdate(double[]... toTransform) {
			visualizerView.updateVisualizer(toTransform[0]);
		}

	}

}
