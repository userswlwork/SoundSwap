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
import java.util.concurrent.atomic.AtomicReference;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Recording;
import android.accounts.Account;
import android.content.Context;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

class Recorder implements Runnable {

  private static final int BYTES_PER_SHORT = Short.SIZE / 8;
  private final AtomicReference<CountDownLatch> mStopLatch =
      new AtomicReference<CountDownLatch>();

  private final Context mContext;
  private final Account mAccount;
  private Recording mRecording;
  private boolean mFinalizedRecording = false;

  public Recorder(Context context, Account account) {
    mContext = context;
    mAccount = account;
  }

  private synchronized RandomAccessFile newInitializedFilePart() {
    File newPart = mRecording.addFilePart(Constants.RECORDING_FILE_EXTENSION);
    try {
      final RandomAccessFile filePart = new RandomAccessFile(newPart, "rw");
      initializeFilePart(filePart);
      return filePart;
    } catch (FileNotFoundException e) {
      // Won't happen; we created the file just now.
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException("Could not initialize file.", e);
    }
  }

  private final int MAX_BYTES_PER_FILEPART = 1024 * 1024;  // 1 megabyte

  @Override
  public void run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

    // Note that the successful execution of the logic below (explained in
    // relevant comments) relies on this executor executing tasks in the order
    // that they are scheduled for execution, and not executing any two tasks
    // concurrently.
    ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    long startTimeMs = System.currentTimeMillis();
    mRecording = Recording.newTempRecording(mContext, mAccount, startTimeMs);
    mRecording.init();

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

    final AtomicReference<RandomAccessFile> output =
        new AtomicReference<RandomAccessFile>();

    try {
      writeExecutor.submit(new InitializeNewFilePartCallable(output));

      audioRecord.startRecording();

      int filePartBytesAssigned = 0;
      while (mStopLatch.get() == null) {
        // If we've sent enough data to the current file part that writing the
        // current buffer might overflow our maximum per-part size limit, then
        // schedule a task to close the current file part and initialize a new
        // one.  The successful operation of this relies on the fact that we're
        // using a single-threaded, sequential executor.
        if (filePartBytesAssigned > MAX_BYTES_PER_FILEPART - bufferSize * 2) {
          writeExecutor.submit(new FinalizeFilePartCallable(output,
              filePartBytesAssigned));
          writeExecutor.submit(new InitializeNewFilePartCallable(output));

          // Now we'll be sending bytes to a new file part, so reset the counter
          filePartBytesAssigned = 0;
        }

        // Allocate a buffer
        final short[] buffer = new short[bufferSize];

        //
        // Read from the audio stream into the buffer
        //
        final int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
        filePartBytesAssigned += bufferReadResult * BYTES_PER_SHORT;

        if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION) {
          throw new IllegalStateException("Invalid audiorecord read "
              + "operation.");
        } else if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
          throw new IllegalStateException("Audiorecord read bad value.");
        }

        // Asynchronously write the buffer to disk
        writeExecutor.submit(new WriteBufferDataCallable(output,
            buffer,
            bufferReadResult));
      }

      // We got notice that we should stop, so stop listening to the microphone,
      // schedule a closure of the current file part, and wait for all scheduled
      // disk operations to complete.
      audioRecord.stop();
      writeExecutor.submit(new FinalizeFilePartCallable(output,
          filePartBytesAssigned));
      writeExecutor.shutdown();

      try {
        writeExecutor.awaitTermination(3600, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Log.e(TAG, "Interrupted while flushing audio to disk.", e);
      }
    } finally {
      CountDownLatch latch = mStopLatch.get();
      if (latch != null) {
        latch.countDown();
      }
    }
  }

  private class InitializeNewFilePartCallable implements Callable<Boolean> {

    private final AtomicReference<RandomAccessFile> mOutput;

    public InitializeNewFilePartCallable(
        AtomicReference<RandomAccessFile> output) {
      mOutput = output;
    }

    @Override
    public Boolean call() {
      Log.i(TAG, "Initializing new file part.");
      RandomAccessFile newOutput = newInitializedFilePart();
      Log.i(TAG, "Initialized new file part.");

      mOutput.set(newOutput);
      Log.i(TAG, "Set new file part.");
      return true;
    }
  }

  private class FinalizeFilePartCallable implements Callable<Boolean> {

    private final AtomicReference<RandomAccessFile> mOutput;
    private final int mBytesWritten;

    public FinalizeFilePartCallable(
        AtomicReference<RandomAccessFile> output,
        int bytesWritten) {
      mOutput = output;
      mBytesWritten = bytesWritten;
    }

    @Override
    public Boolean call() {
      RandomAccessFile oldOutput = mOutput.get();
      try {
        Log.i(TAG, "Finalizing previous file part.");
        finalizeFilePart(oldOutput, mBytesWritten);
        Log.i(TAG, "Finalized previous file part.");
      } catch (IOException e) {
        Log.e(TAG, "Failed to finalize file part.");
      }
      return true;
    }
  }

  private static class WriteBufferDataCallable implements Callable<Boolean> {

    private final AtomicReference<RandomAccessFile> mOutput;
    private final short[] mBuffer;
    private final int mSamplesToWrite;

    public WriteBufferDataCallable(AtomicReference<RandomAccessFile> output,
        short[] buffer,
        int bytesToWrite) {
      mOutput = output;
      mBuffer = buffer;
      mSamplesToWrite = bytesToWrite;
    }

    @Override
    public Boolean call() {
      RandomAccessFile output = mOutput.get();
      for (int i = 0; i < mSamplesToWrite; i++) {
        try {
          output.writeShort(Short.reverseBytes(mBuffer[i]));
        } catch (IOException e) {
          Log.e(TAG, "Could not write to output stream.");
          return false;
        }
      }
      return true;
    }
  }

  private void initializeFilePart(final RandomAccessFile output)
      throws IOException {
    short bSamples = Constants.RECORDING_ENCODING ==
        AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

    // Write file header.
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
  }

  private void finalizeFilePart(final RandomAccessFile output,
      int bytesWritten) throws IOException {
    output.seek(4); // Write size to RIFF header
    output.writeInt(Integer.reverseBytes(36 + bytesWritten));
    output.seek(40); // Write size to Subchunk2Size field
    output.writeInt(Integer.reverseBytes(bytesWritten));
    output.close();
  }

  public void stopRecording(CountDownLatch latch) {
    mStopLatch.set(latch);
  }

  public boolean finalize(Location location) {
    if (location == null) {
      throw new IllegalStateException("Trying to rename temp file without a "
          + "location.");
    }
    int latE6 = (int) (location.getLatitude() * 1E6);
    int lonE6 = (int) (location.getLongitude() * 1E6);
    if (mRecording.moveToRecordedDirectory(latE6, lonE6)) {
      mFinalizedRecording = true;
    }
    return mFinalizedRecording;
  }

  public Recording getFinalRecording() {
    if (!mFinalizedRecording) {
      throw new IllegalStateException("Haven't finalized the recording yet.");
    }
    return mRecording;
  }
}