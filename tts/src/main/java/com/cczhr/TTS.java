package com.cczhr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.speech.tts.SynthesisCallback;

import com.iflytek.business.SpeechConfig;
import com.iflytek.speechcloud.TtsService;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author cczhr
 * @description
 * @since 2021/5/27
 */
public class TTS implements SynthesisCallback {
    private ExecutorService executorService;
    private AudioTrack audioTrack;
    private TtsService ttsService;
    @SuppressLint("StaticFieldLeak")
    private static TTS singleton;
    private volatile int rate = 100;//速度
    private volatile int pitch = 50;//音高
    private Context context;
    private volatile boolean loop = true;
    private volatile boolean isSpeaking = false;
    private Queue<String> textQueue;
    private volatile String text = null;
    private volatile boolean isFlush = false;//音调
    private TTSCallback ttsCallback;

    /**
     * 设置监听回调
     * @param ttsCallback
     */
    public void setTTSCallback(TTSCallback ttsCallback) {
        this.ttsCallback = ttsCallback;
    }

    /**
     * 获取TTS单例对象
     * @return  TTS对象
     */
    public static TTS getInstance() {
        if (singleton == null) {
            synchronized (TTS.class) {
                if (singleton == null) {
                    singleton = new TTS();
                }
            }
        }
        return singleton;
    }

    private TTS() {
        if (textQueue == null)
            textQueue = new ConcurrentLinkedQueue<>();
        if (executorService == null)
            executorService = Executors.newFixedThreadPool(2);
        if (audioTrack == null) {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    16000,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,AudioFormat.ENCODING_PCM_16BIT,
                    AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_CONFIGURATION_MONO , AudioFormat.ENCODING_PCM_16BIT)*2
                    , AudioTrack.MODE_STREAM);
            audioTrack.play();
        }
        executorService.execute(() -> {
            while (loop) {
                text = textQueue.poll();
                if (text != null) {
                    ttsService.onSynthesizeText(text,rate,pitch,this);
                }
                SystemClock.sleep(50L);
            }
        });


    }

    /**
     *
     * @param context 上下文
     * @param speaker  发音人  TTSConstants
     * @param rate 速度
     * @param pitch 音高
     */
    public void init(Context context, String speaker, int rate, int pitch) {
        setRate(rate);
        setPitch(pitch);
        init(context, speaker);
    }


    /**
     * 初始化
     * @param context 上下文
     * @param speaker 发音人  TTSConstants
     */
    public void init(Context context, String speaker) {
        this.context = context.getApplicationContext();
        SpeechConfig.putString(this.context, SpeechConfig.KEY_SPEAKER_SETTING, speaker);
        if (ttsService == null) {
            ttsService = new TtsService();
            ttsService.onCreate(this.context);
        }


    }


    /**
     * 初始化
     * @param context 上下文
     */
    public void init(Context context) {
        init(context,TTSConstants.TTS_XIAOYAN);
    }



    /**
     * 添加到播放合成语音队列中
     * @param text 待播放的文本
     */
    public void speakText(String text) {
        textQueue.offer(text);
        audioTrack.play();

    }

    /**
     * 停止播放
     */
    public void stop() {
        textQueue.clear();
        audioTrack.pause();
        audioTrack.flush();
    }

    /**
     * 停止当前的播放并清空待播放队列，播放新的合成语音
     * @param text 待播放的文本
     */
    public void stopAndSpeakText(String text) {
        if(!isFlush){
            isFlush=true;
            executorService.execute(() -> {
                stop();
                SystemClock.sleep(200);
                speakText(text);
                isFlush=false;
            });
        }
    }


    /**
     * 释放资源
     */
    public void release() {
        loop = false;
        if (audioTrack != null)
            audioTrack.release();
        if (executorService != null)
            executorService.shutdownNow();
        if (ttsService != null)
            ttsService.onDestroy();
        audioTrack = null;
        executorService = null;
        ttsService = null;
        singleton = null;
        context=null;
    }

    /**
     * 是否正在播放语音
     * @return true 表示正在播放中 false 表示已经播放完毕
     */
    public boolean isSpeaking() {
        return !(!isSpeaking&&textQueue.size()==0);
    }

    @Override
    public int getMaxBufferSize() {
        return 0;
    }

    @Override
    public int start(int sampleRateInHz, int audioFormat, int channelCount) {
        isSpeaking=true;
        if (ttsCallback!= null)
            ttsCallback.onStart();
        return 0;
    }

    @Override
    public int audioAvailable(byte[] buffer, int offset, int length) {
        audioTrack.write(buffer, offset, length);
        return 0;
    }

    @Override
    public int done() {
        isSpeaking=false;
        if (ttsCallback!= null)
            ttsCallback.onFinish();
        return 0;
    }

    @Override
    public void error() {

    }

    @Override
    public void error(int errorCode) {

    }

    @Override
    public boolean hasStarted() {
        return false;
    }

    @Override
    public boolean hasFinished() {
        return false;
    }


    /**
     * 获取播放速度
     * @return 速度
     */
    public int getRate() {
        return rate;
    }

    /**
     * 播放速度
     * @param rate 速度 取值为 60 80 100 150 200 越高速度越快
     */
    public void setRate(int rate) {
        this.rate = rate;
    }

    /**
     * 获取音高
     * @return 音高
     */
    public int getPitch() {
        return pitch;
    }

    /**
     * 音高 50 为正常音高 取值0-100
     * @param pitch 音高
     */
    public void setPitch(int pitch) {
        this.pitch = pitch;
    }
}
