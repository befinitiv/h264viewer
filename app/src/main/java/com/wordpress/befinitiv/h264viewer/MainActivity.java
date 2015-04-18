package com.wordpress.befinitiv.h264viewer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private UdpReceiverDecoderThread mRec = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(mRec == null) {
            mRec = new UdpReceiverDecoderThread(holder.getSurface(), 5000);
            mRec.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(mRec != null) {
            mRec.interrupt();
        }
    }


    private class UdpReceiverDecoderThread extends Thread {
        int port;
        int nalu_search_state = 0;
        byte [] nalu_data;
        int nalu_data_position;
        int NALU_MAXLEN = 1024 * 1024;
        long dbg = 0;

        private MediaCodec decoder = null;
        private MediaFormat format = null;

        public UdpReceiverDecoderThread(Surface surface, int port) {

            this.port = port;
            nalu_data = new byte[NALU_MAXLEN];
            nalu_data_position = 0;

            try {
                decoder = MediaCodec.createDecoderByType("video/avc");
            } catch(IOException e) {}

            format = MediaFormat.createVideoFormat("video/avc", 1920, 1080);
            decoder.configure(format, surface, null, 0);
            decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            decoder.start();
        }

        public void run() {

 /*           java.io.FileInputStream in = null;
            try {
                in = new java.io.FileInputStream("/data/data/com.wordpress.befinitiv.h264viewer/pipe/jo");
            } catch(FileNotFoundException e) {Log.d("UDP", "Error opening pipe");}

            for(;;) {
                byte buffer2[] = new byte[18800 * 8 * 8 * 8];
                int sampleSize = 0;
                try {
                    sampleSize = in.read(buffer2, 0, 1024);
                } catch (IOException e) {
                }


                if (sampleSize < 0) {
                    Log.d("UDP", "End of stream");
                    break;

                } else {
                    parseDatagram(buffer2, sampleSize);
                }
            }*/


            String text;
            int server_port = this.port;
            byte[] message = new byte[1500];
            DatagramPacket p = new DatagramPacket(message, message.length);
            DatagramSocket s = null;
            try {
                s = new DatagramSocket(server_port);
            } catch (SocketException e) {}

            while(!Thread.interrupted() && s!= null) {
                try {
                    s.receive(p);
                } catch (IOException e) {}

                parseDatagram(message, p.getLength());
            }
            if(s != null)
                s.close();

            decoder.stop();

        }

        private void feedDecoder(byte[] n, int len) {

            for(;;) {
                BufferInfo bi = new MediaCodec.BufferInfo();
                int outputBufferIndex = decoder.dequeueOutputBuffer(bi, 0);
                if (outputBufferIndex >= 0) {
                    // if API level >= 21, get output buffer here
                    //ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    decoder.releaseOutputBuffer(outputBufferIndex, true);


                    /*long fdelay = System.currentTimeMillis() - dbg;
                    Log.d("UDP", "Frame delay " + fdelay);
                    dbg = System.currentTimeMillis();*/


                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d("UDP", "output format changed");
                }


                int inputBufferIndex = decoder.dequeueInputBuffer(200);
                if (inputBufferIndex >= 0) {
                    //Log.d("UDP", "queuing buffer");
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.put(n, 0, len);
                    decoder.queueInputBuffer(inputBufferIndex, 0, len, 0, 0);
                    break;
                }
                // else Log.d("UDP", "no buffer available");
            }



        }
        private void interpretNalu(byte[] n, int len) {
            //int id = n[4] & 0xff;
            feedDecoder(n, len);
        }

        private void parseDatagram(byte[] p, int plen) {
            int i;

            for(i=0; i<plen; ++i) {
                nalu_data[nalu_data_position++] = p[i];
                if(nalu_data_position == NALU_MAXLEN -1) {
                    Log.d("UDP", "Nalu overflow");
                    nalu_data_position = 0;
                }

                switch (nalu_search_state) {
                    case 0:
                    case 1:
                    case 2:
                        if(p[i] == 0)
                            nalu_search_state++;
                        else
                            nalu_search_state =0;
                        break;

                     case 3:
                        if(p[i] == 1) {
                            //nalupacket found

                            nalu_data[0] = 0;
                            nalu_data[1] = 0;
                            nalu_data[2] = 0;
                            nalu_data[3] = 1;
                            interpretNalu(nalu_data, nalu_data_position -4);
                            nalu_data_position = 4;
                        }
                        nalu_search_state = 0;

                        break;

                    default:
                        break;




                }
            }
        }
    }
}