package com.kx3panadapterfft;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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
import ca.uol.aig.fftpack.RealDoubleFFT;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.kx3panadapterfft.wav.WavFile;

public class SoundRecordAndAnalysisActivity extends Activity {
	private static final String TAG = "SoundRecordAndAnalysisActivity";
	public static final boolean MIC = true;
	public static final boolean PLAYBACK = false;
	public static boolean STEREO=true;
	RecordAudio recordTask;
	ImageView imageViewDisplaySectrum;
	Bitmap bitmapDisplaySpectrum;
	Canvas canvasDisplaySpectrum;

	Paint paintSpectrumDisplay;
	private long backPressedTime = 0; // used by onBackPressed()
	private int rate = 44100;
	private final int channelConfiguration = AudioFormat.CHANNEL_IN_DEFAULT;
	private final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	private int blockSize = 1024;
	private VisualizerView visualizerView;
	
	public static UsbSerialDriver serialDriver;
	public static String serialUsbDevice;

	public static UsbSerialDriver audioDriver;
	public static String audioUsbDevice;
	
	private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	 private SerialInputOutputManager mSerialIoManager;
	 private String rest="";
	    private final SerialInputOutputManager.Listener mListener =
	            new SerialInputOutputManager.Listener() {

	        @Override
	        public void onRunError(Exception e) {
	            Log.d(TAG, "Runner stopped.");
	        }

	        @Override
	        public void onNewData(final byte[] data) {
	        	/*
	        	RSP format: IF[f]*****+yyyyrx*00tmvspbd1*; where the fields are defined as follows: 
	        		 
	        		[f] Operating frequency, excluding any RIT/XIT offset (11 digits; see FA command format) 
	        		* represents a space (BLANK, or ASCII 0x20) 
	        		+ either "+" or "-" (sign of RIT/XIT offset) 
	        		yyyy RIT/XIT offset in Hz (range is -9999 to +9999 Hz when computer-controlled) 
	        		r 1 if RIT is on, 0 if off 
	        		x 1 if XIT is on, 0 if off 
	        		t 1 if the K3 is in transmit mode, 0 if receive 
	        		m operating mode (see MD command) 
	        		v receive-mode VFO selection, 0 for VFO A, 1 for VFO B 
	        		s 1 if scan is in progress, 0 otherwise 
	        		p 1 if the transceiver is in split mode, 0 otherwise 
	        		b Basic RSP format: always 0; K2 Extended RSP format (K22): 1 if present IF response 
	        		is due to a band change; 0 otherwise 
	        		 d Basic RSP format: always 0; K3 Extended RSP format (K31): DATA sub-mode, 
	        		if applicable (0=DATA A, 1=AFSK A, 2= FSK D, 3=PSK D) 
*/
	        	
	        	String str="";
				try {
					str = new String(data, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					 Log.e(TAG, e.getMessage());
				}
				str=rest+str;
				 Log.i(TAG, str);
				 
				 double fa=0;
				 double fb=0;
				 int index=0;
				 String[] parts=str.split(";");
				for(String part: parts){
		        	if(part.startsWith("FA")){
		        		fa=Double.parseDouble(part.substring(2, part.indexOf(' ')>0?part.indexOf(' '):part.length()));
		        	}
		        	if(part.startsWith("FB")){
		        		fb=Double.parseDouble(part.substring(2, part.indexOf(' ')>0?part.indexOf(' '):part.length()));
		        	}
		        	if(index==parts.length){
		        		if(str.toCharArray()[str.length()]!=';'){
		        			rest=part;
		        		}
		        	}
		        	index++;
				}
				final double freqA=fa;
				final double freqB=fb;
				runOnUiThread(new Runnable() {
	        	     @Override
	        	     public void run() {
	        	    	 visualizerView = (VisualizerView) findViewById(R.id.visualizerView);
	        	    	 visualizerView.updateVisualizer(freqA,freqB);

	        	    }
	        	});
	        	
	        	
	        	
	        	
	        	
	        }
	    };
	
	
	private void init(){
		
		if (recordTask != null) {
			recordTask.init();
		}
	
	}
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		init();
	
	}

	@Override
	public void onBackPressed() {

	

		long t = System.currentTimeMillis();
		if (t - backPressedTime > 2000) { // 2 secs
			backPressedTime = t;
			Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
		} else { // this guy is serious
			// clean up
			if (recordTask != null) {
				recordTask.cancel(true);
			}
			//super.onBackPressed(); // bye
			  Intent intent = new Intent(Intent.ACTION_MAIN);
		        intent.addCategory(Intent.CATEGORY_HOME);
		        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		        startActivity(intent);
		}

	}

	@Override
	public void onStop(){
		super.onStop();
		if (recordTask != null) {
			recordTask.cancel(true);
		}
	}
	@Override
	public void onStart() {
		super.onStart();
		if(SoundRecordAndAnalysisActivity.serialUsbDevice==null){
			 final Intent intent = new Intent(this, DeviceListActivity.class);
			 intent.putExtra("title",getString(R.string.kx3connection));
		     this.startActivity(intent);
		}else{
			if(SoundRecordAndAnalysisActivity.audioUsbDevice==null){
				 final Intent intent = new Intent(this, DeviceListActivity.class);
				 intent.putExtra("title",getString(R.string.usbaudioconnection));
			     this.startActivity(intent);
			}else{
		if (recordTask == null) {

			recordTask = new RecordAudio();
			recordTask.execute();

		}
			}
		}
	}
	
	@Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (serialDriver != null) {
            try {
                serialDriver.close();
            } catch (IOException e) {
                // Ignore.
            }
            serialDriver = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
      
        
        Log.d(TAG, "Resumed, sDriver=" + serialDriver);
        if (serialDriver == null && serialUsbDevice!=null) {
        	  UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
              for (final UsbDevice device : mUsbManager.getDeviceList().values()) {
            	  if(device.getDeviceName().equals(serialUsbDevice)){
            		  serialDriver = UsbSerialProber.acquire(mUsbManager, device);
            	  }
              }
        } 
        
        if (serialDriver != null) {
            try {
                serialDriver.open();
                serialDriver.setBaudRate(38400);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
             
                try {
                    serialDriver.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                serialDriver = null;
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
        if (serialDriver != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(serialDriver, mListener);
           
            mExecutor.submit(mSerialIoManager);
            try {
				serialDriver.write("AI2;".getBytes(), 1000);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            //mSerialIoManager.writeAsync("FA00001550000;MD5;FA00001550000;".getBytes());
            //mSerialIoManager.writeAsync("IF;".getBytes());
            

        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
      super.onSaveInstanceState(savedInstanceState);

      savedInstanceState.putString("usbDevice", serialUsbDevice);
    
    }
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
      super.onRestoreInstanceState(savedInstanceState);
    
      serialUsbDevice = savedInstanceState.getString("usbDevice");
      UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
      for (final UsbDevice device : mUsbManager.getDeviceList().values()) {
    	  if(device.getDeviceName().equals(serialUsbDevice)){
    		  serialDriver = UsbSerialProber.acquire(mUsbManager, device);
    	  }
      }
    }

	private class RecordAudio extends AsyncTask<Void, double[], Void> {
		private static final String TAG = "RecordAudio";

		private ComplexDoubleFFT complexTransformer;
		private RealDoubleFFT realTransformer;
		short[] playBuffer;

		short[] buffer;
		double[] doubleBuffer;
		double[] toTransform ;

		protected void init(){
			if(STEREO){
				complexTransformer = new ComplexDoubleFFT(blockSize / 2);
				realTransformer=null;
			}else{
				realTransformer = new RealDoubleFFT(blockSize);
				complexTransformer=null;
			}
			if (PLAYBACK) {
				playBuffer = new short[blockSize * 10];
			}
			buffer = new short[blockSize];
			doubleBuffer = new double[blockSize];
			toTransform = new double[doubleBuffer.length];
			
			
			visualizerView = (VisualizerView) findViewById(R.id.visualizerView);
			visualizerView.init(blockSize, rate);
		}

		public RecordAudio() {
			init();
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
				int bufferSize=blockSize;
				 for (int myRate : new int[] {44100, 22050, 11025, 8000}) {  // add the rates you wish to check against
					 bufferSize = AudioRecord.getMinBufferSize(myRate, channelConfiguration, audioEncoding);
				        if (bufferSize > 0) {
				            // buffer size is valid, Sample rate supported
				        	rate=myRate;
				        	blockSize=Math.min(blockSize,bufferSize/2);
				        	break;
				        }
				    }
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfiguration, audioEncoding,	bufferSize);
				if(audioRecord.getChannelConfiguration()!=AudioFormat.CHANNEL_IN_STEREO){
					STEREO=false;
				}
				init();
				
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
					if(STEREO){
						framesRead = audioRecord.read(buffer, 0, blockSize/2);
						framesRead = framesRead / 2;
					}else{
						framesRead = audioRecord.read(buffer, 0, blockSize);
					}
					

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
				if(STEREO){
				double[] doubleBufferLeft = new double[doubleBuffer.length / 2];
				double[] doubleBufferRight = new double[doubleBuffer.length / 2];

				// double[] doubleBuffer = shortArrayToDoubleArray(buffer);

					for (int i = 0; i < doubleBuffer.length; i++) {
						if (i % 2 == 0) {
							doubleBufferLeft[i / 2] = doubleBuffer[i];
						} else {
							doubleBufferRight[i / 2] = doubleBuffer[i];
						}
					}

				
				for (int i = 0; i < Math.min(blockSize, framesRead); i++) {
					
						toTransform[i * 2] = doubleBufferLeft[i];// / 32768.0;
						toTransform[i * 2 + 1] = doubleBufferRight[i];// / 32768.0;
					}
				}else{
					
					for (int i = 0; i < Math.min(blockSize, framesRead); i++) {
						
							toTransform[i] = doubleBuffer[i];

						}
				}
					if(STEREO){
						complexTransformer.ft(toTransform);
					}else{
						realTransformer.ft(toTransform);
					}

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

		  @Override
		    protected void onCancelled() {
			  Log.e(TAG, "Stoping");
		    }

		private double[] shortArrayToDoubleArray(short[] data) {
			double[] result = new double[data.length];

			for (int j = 0; j < data.length; j++) {
				result[j] = data[j]/(Math.pow(2, 16)/2);
				if(result[j]>1 || result[j]<-1){
					Log.d(TAG, " "+result[j]);
				}
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
