package com.base.basepedo.service;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.base.basepedo.utils.CountDownTimer;

import java.util.Timer;
import java.util.TimerTask;

public class StepDcretor implements SensorEventListener {
    //存放三轴数据
    float[] oriValues = new float[3];
    final int valueNum = 4;
    //用于存放计算阈值的波峰波谷差值
    float[] tempValue = new float[valueNum];
    int tempCount = 0;
    //是否上升的标志位
    boolean isDirectionUp = false;
    //持续上升次数
    int continueUpCount = 0;
    //上一点的持续上升的次数，为了记录波峰的上升次数
    int continueUpFormerCount = 0;
    //上一点的状态，上升还是下降
    boolean lastStatus = false;
    //波峰值
    float peakOfWave = 0;
    //波谷值
    float valleyOfWave = 0;
    //此次波峰的时间
    long timeOfThisPeak = 0;
    //上次波峰的时间
    long timeOfLastPeak = 0;
    //当前的时间
    long timeOfNow = 0;
    //当前传感器的值
    float gravityNew = 0;
    //上次传感器的值
    float gravityOld = 0;
    //动态阈值需要动态的数据，这个值用于这些动态数据的阈值
    final float initialValue = (float) 1.8;
    //初始阈值
    float ThreadValue = (float) 2.0;


    float avg_v = 0;
    float min_v = 0;
    float max_v = 0;

    int acc_count = 0;
    int up_c = 0;
    int down_c = 0;
    long pre_time = 0;


    private final String TAG = "StepDcretor";
    // alpha 由 t / (t + dT)计算得来，其中 t 是低通滤波器的时间常数，dT 是事件报送频率
    private final float alpha = 0.8f;
    private long perCalTime = 0;

    //最新修改的精度值
    private final float minValue = 9.8f;
    private final float maxValue = 9.9f;
    //9.5f
//    private final float verminValue = 8.5f;
    //10.0f
//    private final float vermaxValue = 11.5f;
    private final float minTime = 150;
    private final float maxTime = 2000;
    /**
     * 0-准备计时   1-计时中  2-准备为正常计步计时  3-正常计步中
     */
    private int CountTimeState = 0;
    public static int CURRENT_SETP = 0;
    public static int TEMP_STEP = 0;
    private int lastStep = -1;
    // 加速计的三个维度数值
    public static float[] gravity = new float[3];
    public static float[] linear_acceleration = new float[3];
    //用三个维度算出的平均值
    public static float average = 0;

    private Timer timer;
    // 倒计时5秒，5秒内不会显示计步，用于屏蔽细微波动
    private long duration = 4000;
    private TimeCount time;

    OnSensorChangeListener onSensorChangeListener;

    public StepDcretor(Context context) {
        super();
    }

    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        synchronized (this) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                calc_step(event);
            }
        }
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {

    }

    public interface OnSensorChangeListener {
        void onChange();
    }

    public OnSensorChangeListener getOnSensorChangeListener() {
        return onSensorChangeListener;
    }

    public void setOnSensorChangeListener(
            OnSensorChangeListener onSensorChangeListener) {
        this.onSensorChangeListener = onSensorChangeListener;
    }

    class TimeCount extends CountDownTimer {
        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
            // 如果计时器正常结束，则开始计步
            time.cancel();
            CURRENT_SETP += TEMP_STEP;
            lastStep = -1;
//            CountTimeState = 2;
            Log.v(TAG, "计时正常结束");

            timer = new Timer(true);
            TimerTask task = new TimerTask() {
                public void run() {
                    if (lastStep == CURRENT_SETP) {
                        timer.cancel();
                        CountTimeState = 0;
                        lastStep = -1;
                        TEMP_STEP = 0;
                        Log.v(TAG, "停止计步：" + CURRENT_SETP);
                    } else {
                        lastStep = CURRENT_SETP;
                    }
                }
            };
            timer.schedule(task, 0, 3000);
            CountTimeState = 3;
        }

        @Override
        public void onTick(long millisUntilFinished) {
            if (lastStep == TEMP_STEP) {
                Log.v(TAG, "onTick 计时停止");
                time.cancel();
                CountTimeState = 0;
                lastStep = -1;
                TEMP_STEP = 0;
            } else {
                lastStep = TEMP_STEP;
            }
        }

    }


    void avg_check_v(float v) {
        acc_count++;
        //求移动平均线
        //50ms 1 second 20 , 3 sec60;
        if (acc_count < 34) {
            //avg_v=((acc_count-1)*avg_v+v)/acc_count;
            avg_v = avg_v + (v - avg_v) / acc_count;
        } else {
            //avg_v=(avg_v*99+v)/100;
            avg_v = avg_v * 33 / 34 + v / 34;
        }

        if (v > avg_v) {
            up_c++;
            if (up_c == 1) {
                //Log.e("wokao","diff:"+(max_v-min_v));
                max_v = avg_v;
            } else {
                max_v = Math.max(v, max_v);
            }
            if (up_c >= 2) {
                down_c = 0;
            }
        } else {
            down_c++;
            if (down_c == 1) {
                min_v = v;
            } else {
                min_v = Math.min(v, min_v);
            }
            if (down_c >= 2) {
                up_c = 0;
            }
        }
        //Log.e("wokao","avg_v:"+avg_v+",v:"+v+",uc"+up_c+",dc:"+down_c);

        if (up_c == 2 && (max_v - min_v) > 2) {
            //
            long cur_time = System.currentTimeMillis();
            if (cur_time - pre_time >= 500
                    ) {
                pre_time = cur_time;
                preStep();
                Log.e("xfblog", "CURRENT_SETP:" + CURRENT_SETP);
            } else {
                up_c = 1;
            }
        }
    }

    synchronized private void calc_step(SensorEvent event) {
        average = (float) Math.sqrt(Math.pow(event.values[0], 2)
                + Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2));
        //    avg_check_v(average);
        DetectorNewStep(average);
    }

    private void preStep() {
        if (CountTimeState == 0) {
            // 开启计时器
            time = new TimeCount(duration, 700);
            time.start();
            CountTimeState = 1;
            Log.v(TAG, "开启计时器");
        } else if (CountTimeState == 1) {
            TEMP_STEP++;
            Log.v(TAG, "计步中 TEMP_STEP:" + TEMP_STEP);
        } else if (CountTimeState == 3) {
            CURRENT_SETP++;
            if (onSensorChangeListener != null) {
                onSensorChangeListener.onChange();
            }
        }
    }

    private void oldCalStep(SensorEvent event) {
        // 用低通滤波器分离出重力加速度
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        average = (float) Math.sqrt(Math.pow(gravity[0], 2)
                + Math.pow(gravity[1], 2) + Math.pow(gravity[2], 2));

//                if (average <= verminValue) {
        if (average <= minValue) {
            Log.v("xfblog", "低");
            perCalTime = System.currentTimeMillis();
        }
//                } else if (average >= vermaxValue) {
        else if (average >= maxValue) {
            Log.v("xfblog", "高");
            float betweentime = System.currentTimeMillis()
                    - perCalTime;
            if (betweentime >= minTime && betweentime < maxTime) {
                perCalTime = 0;
                if (CountTimeState == 0) {
                    // 开启计时器
                    time = new TimeCount(duration, 800);
                    time.start();
                    CountTimeState = 1;
                    Log.v(TAG, "开启计时器");
                } else if (CountTimeState == 1) {
                    TEMP_STEP++;
                    Log.v(TAG, "计步中 TEMP_STEP:" + TEMP_STEP);
                }
//                        else if (CountTimeState == 2) {
//                            timer = new Timer(true);
//                            TimerTask task = new TimerTask() {
//                                public void run() {
//                                    if (lastStep == CURRENT_SETP) {
//                                        timer.cancel();
//                                        CountTimeState = 0;
//                                        lastStep = -1;
//                                        TEMP_STEP = 0;
//                                        Log.v(TAG, "停止计步：" + CURRENT_SETP);
//                                    } else {
//                                        lastStep = CURRENT_SETP;
//                                    }
//                                }
//                            };
//                            timer.schedule(task, 0, 2000);
//                            CountTimeState = 3;
//                        }
                else if (CountTimeState == 3) {
                    CURRENT_SETP++;
                    if (onSensorChangeListener != null) {
                        onSensorChangeListener.onChange();
                    }
                }


            }
        }
//                  }
    }


    /*
     * 检测步子，并开始计步
	 * 1.传入sersor中的数据
	 * 2.如果检测到了波峰，并且符合时间差以及阈值的条件，则判定为1步
	 * 3.符合时间差条件，波峰波谷差值大于initialValue，则将该差值纳入阈值的计算中
	 * */
    public void DetectorNewStep(float values) {
        if (gravityOld == 0) {
            gravityOld = values;
        } else {
            if (DetectorPeak(values, gravityOld)) {
                timeOfLastPeak = timeOfThisPeak;
                timeOfNow = System.currentTimeMillis();
                if (timeOfNow - timeOfLastPeak >= 250
                        && (peakOfWave - valleyOfWave >= ThreadValue) && timeOfNow - timeOfLastPeak <= 2000) {
                    timeOfThisPeak = timeOfNow;
					/*
					 * 更新界面的处理，不涉及到算法
					 * 一般在通知更新界面之前，增加下面处理，为了处理无效运动：
					 * 1.连续记录10才开始计步
					 * 2.例如记录的9步用户停住超过3秒，则前面的记录失效，下次从头开始
					 * 3.连续记录了9步用户还在运动，之前的数据才有效
					 * */
                    StepDcretor.CURRENT_SETP++;
                    if (onSensorChangeListener != null) {
                        onSensorChangeListener.onChange();
                    }
                }
                if (timeOfNow - timeOfLastPeak >= 250
                        && (peakOfWave - valleyOfWave >= initialValue)) {
                    timeOfThisPeak = timeOfNow;
                    ThreadValue = Peak_Valley_Thread(peakOfWave - valleyOfWave);
                }
            }
        }
        gravityOld = values;
    }

    /*
     * 检测波峰
     * 以下四个条件判断为波峰：
     * 1.目前点为下降的趋势：isDirectionUp为false
     * 2.之前的点为上升的趋势：lastStatus为true
     * 3.到波峰为止，持续上升大于等于2次
     * 4.波峰值大于20
     * 记录波谷值
     * 1.观察波形图，可以发现在出现步子的地方，波谷的下一个就是波峰，有比较明显的特征以及差值
     * 2.所以要记录每次的波谷值，为了和下次的波峰做对比
     * */
    public boolean DetectorPeak(float newValue, float oldValue) {
        lastStatus = isDirectionUp;
        if (newValue >= oldValue) {
            isDirectionUp = true;
            continueUpCount++;
        } else {
            continueUpFormerCount = continueUpCount;
            continueUpCount = 0;
            isDirectionUp = false;
        }

        if (!isDirectionUp && lastStatus
                && (continueUpFormerCount >= 2 || oldValue >= 15)) {
            peakOfWave = oldValue;
            return true;
        } else if (!lastStatus && isDirectionUp) {
            valleyOfWave = oldValue;
            return false;
        } else {
            return false;
        }
    }

    /*
     * 阈值的计算
     * 1.通过波峰波谷的差值计算阈值
     * 2.记录4个值，存入tempValue[]数组中
     * 3.在将数组传入函数averageValue中计算阈值
     * */
    public float Peak_Valley_Thread(float value) {
        float tempThread = ThreadValue;
        if (tempCount < valueNum) {
            tempValue[tempCount] = value;
            tempCount++;
        } else {
            tempThread = averageValue(tempValue, valueNum);
            for (int i = 1; i < valueNum; i++) {
                tempValue[i - 1] = tempValue[i];
            }
            tempValue[valueNum - 1] = value;
        }
        return tempThread;

    }

    /*
     * 梯度化阈值
     * 1.计算数组的均值
     * 2.通过均值将阈值梯度化在一个范围里
     * */
    public float averageValue(float value[], int n) {
        float ave = 0;
        for (int i = 0; i < n; i++) {
            ave += value[i];
        }
        ave = ave / valueNum;
        if (ave >= 8)
            ave = (float) 4.3;
        else if (ave >= 7 && ave < 8)
            ave = (float) 3.3;
        else if (ave >= 4 && ave < 7)
            ave = (float) 2.3;
        else if (ave >= 3 && ave < 4)
            ave = (float) 2.0;
        else {
            ave = (float) 1.3;
        }
        return ave;
    }
}
