package net.peterd.soundswap.soundservice;

import static net.peterd.soundswap.Constants.TAG;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.peterd.soundswap.Constants;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

class Recorder implements Runnable {

  private static final int BYTES_PER_SHORT = Short.SIZE / 8;
  private final AtomicReference<CountDownLatch> mStopLatch =
      new AtomicReference<CountDownLatch>();
  private final File mOutputFile;

  public Recorder(File outputFile) {
    mOutputFile = outputFile;
  }

  @Override
  public void run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
    ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    try {
      final RandomAccessFile output = new RandomAccessFile(mOutputFile, "rw");

      short bSamples = Constants.RECORDING_ENCODING ==
          AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

      // Write file header.
      try {
        output.setLength(0); // Set file length to 0, to prevent unexpected
                             // behavior in case the file already existed
        output.writeBytes("RIFF");
        output.writeInt(0); // Final file size not known yet, write 0
        output.writeBytes("WAVE");
        output.writeBytes("fmt ");
        output.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for
                                                   // PCM
        output.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1
                                                          // for PCM
        output.writeShort(Short.reverseBytes((short) 1)); // Number of
                                                          // channels, 1 for
                                                          // mono
        // Sample rate
        output.writeInt(Integer.reverseBytes(Constants.RECORDING_SAMPLE_RATE));
        output.writeInt(Integer.reverseBytes(Constants.RECORDING_SAMPLE_RATE
            * bSamples / 8)); // Byte rate,
                              // SampleRate*NumberOfChannels*BitsPerSample/8
        output.writeShort(Short.reverseBytes((short) (bSamples / 8))); // Block
                                                                       // align,
                                                                       // NumberOfChannels*BitsPerSample/8
        output.writeShort(Short.reverseBytes(bSamples)); // Bits per sample
        output.writeBytes("data");
        output.writeInt(0); // Data chunk size not known yet, write 0
      } catch (IOException e) {
        Log.e(TAG, "Failed to write wav file header.", e);
        return;
      }

      int bufferSize = AudioRecord.getMinBufferSize(
          Constants.RECORDING_SAMPLE_RATE,
          Constants.RECORDING_CHANNEL,
          Constants.RECORDING_ENCODING);

      AudioRecord audioRecord = new AudioRecord(
          MediaRecorder.AudioSource.MIC,
          Constants.RECORDING_SAMPLE_RATE,
          Constants.RECORDING_CHANNEL,
          Constants.RECORDING_ENCODING,
          bufferSize);

      audioRecord.startRecording();

      int bytesRead = 0;
      final AtomicInteger bytesWritten = new AtomicInteger(0);
      final AtomicInteger bytesToWrite = new AtomicInteger(0);
      while (mStopLatch.get() == null) {
        // Allocate a buffer
        final short[] buffer = new short[bufferSize];

        // Read from the audio stream into the buffer
        final int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
        bytesRead += bufferReadResult * BYTES_PER_SHORT;

        if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION) {
          throw new IllegalStateException("Invalid audiorecord read "
              + "operation.");
        } else if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
          throw new IllegalStateException("Audiorecord read bad value.");
        }

        // Asynchronously write the buffer to disk
        bytesToWrite.addAndGet(bufferReadResult * BYTES_PER_SHORT);
        writeExecutor.submit(new Callable<Boolean>() {
              @Override
              public Boolean call() {
                for (int i = 0; i < bufferReadResult; i++) {
                  try {
                    output.writeShort(Short.reverseBytes(buffer[i]));
                    bytesWritten.addAndGet(BYTES_PER_SHORT);
                    bytesToWrite.addAndGet(-1 * BYTES_PER_SHORT);
                  } catch (IOException e) {
                    Log.e(TAG, "Could not write to output stream.");
                    return false;
                  }
                }
                return true;
              }
            });

        Log.d(TAG, "Recording... bytes read: " + bytesRead +
            "; bytes written: " + bytesWritten.get() +
            "; bytes in write buffer: " + bytesToWrite.get());
      }

      // We got notice that we should stop, so stop listening to the microphone,
      // and wait for all buffered data to flush to disk
      audioRecord.stop();
      writeExecutor.shutdown();
      try {
        writeExecutor.awaitTermination(3600, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Log.e(TAG, "Interrupted while flushing audio to disk.", e);
      }

      // Update wav file header and close it.

      try {
        output.seek(4); // Write size to RIFF header
        output.writeInt(Integer.reverseBytes(36 + bytesWritten.get()));
        output.seek(40); // Write size to Subchunk2Size field
        output.writeInt(Integer.reverseBytes(bytesWritten.get()));
        output.close();
      } catch (IOException e) {
        Log.e(TAG, "Could not close output file.");
      }
    } catch (FileNotFoundException e) {
      // Won't happen; we created the file just now.
      throw new RuntimeException(e);
    } finally {
      CountDownLatch latch = mStopLatch.get();
      if (latch != null) {
        latch.countDown();
      }
    }
  }

  public void stopRecording(CountDownLatch latch) {
    mStopLatch.set(latch);
  }
}