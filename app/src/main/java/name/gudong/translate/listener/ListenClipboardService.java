/*
 *  Copyright (C) 2015 GuDong <gudong.name@gmail.com>
 *
 *  This file is part of GdTranslate
 *
 *  GdTranslate is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  GdTranslate is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with GdTranslate.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package name.gudong.translate.listener;

import android.animation.Animator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.view.View;
import android.widget.ImageView;

import com.orhanobut.logger.Logger;
import com.umeng.analytics.MobclickAgent;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import name.gudong.translate.GDApplication;
import name.gudong.translate.listener.view.TipView;
import name.gudong.translate.listener.view.TipViewController;
import name.gudong.translate.mvp.model.entity.Result;
import name.gudong.translate.mvp.model.type.EIntervalTipTime;
import name.gudong.translate.mvp.presenters.BasePresenter;
import name.gudong.translate.mvp.presenters.ClipboardPresenter;
import name.gudong.translate.mvp.views.ITipFloatView;
import name.gudong.translate.reject.components.DaggerActivityComponent;
import name.gudong.translate.reject.modules.ActivityModule;
import name.gudong.translate.util.SpUtils;


public final class ListenClipboardService extends Service implements ITipFloatView, TipView.ITipViewListener {
    private static final String KEY_FOR_WEAK_LOCK = "weak-lock";
    @Inject
    ClipboardPresenter mPresenter;
    @Inject
    TipViewController mTipViewController;

    @Override
    public void onCreate() {
        setUpInject();
        addListener();
        attachView();
        mPresenter.onCreate();
    }

    private void attachView() {
        mPresenter.attachView(this);
    }

    private void addListener() {
        mPresenter.addListener();
    }

    private void setUpInject() {
//        DaggerServiceComponent.builder()
//                .serviceModule(new ServiceModule(this))
//                .appComponent(GDApplication.getAppComponent())
//                .build()
//                .inject(this);

        DaggerActivityComponent.builder()
                .appComponent(GDApplication.getAppComponent())
                .activityModule(new ActivityModule(this))
                .build()
                .inject(this);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getBooleanExtra(KEY_FOR_WEAK_LOCK, false)) {
                BootCompletedReceiver.completeWakefulIntent(intent);
            }
        }
        boolean isOpen = SpUtils.getReciteOpenOrNot(this);
        if(isOpen){
            EIntervalTipTime tipTime = SpUtils.getIntervalTimeWay(GDApplication.mContext);
            int time = tipTime.getIntervalTime();
            boolean isSecond = tipTime == EIntervalTipTime.THIRTY_SECOND;
            TimeUnit unit = isSecond? TimeUnit.SECONDS:TimeUnit.MINUTES;
            Logger.i("====","开启背单词 每 "+time+" "+unit.name()+"显示一次");
            //设置定时显示任务
            mPresenter.openTipCyclic(time,unit);
        }else {
            Logger.i("====","移除背单词");
            mPresenter.removeTipCyclic();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ProcessBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onDestroy();
    }

    public static void start(Context context) {
        Intent serviceIntent = new Intent(context, ListenClipboardService.class);
        context.startService(serviceIntent);
    }

    public static void startForWeakLock(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, ListenClipboardService.class);
        context.startService(serviceIntent);

        intent.putExtra(ListenClipboardService.KEY_FOR_WEAK_LOCK, true);
        Intent myIntent = new Intent(context, ListenClipboardService.class);

        // using wake lock to start service
        WakefulBroadcastReceiver.startWakefulService(context, myIntent);
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void errorPoint(String error) {
        mTipViewController.showErrorInfo(error,this);
    }

    @Override
    public void showResult(Result result, boolean isShowFavorite) {
        mTipViewController.show(result, isShowFavorite, this);
    }

    @Override
    public void initWithFavorite(Result result) {
        mTipViewController.setWithFavorite(result);
    }

    @Override
    public void initWithNotFavorite(Result result) {
        mTipViewController.setWithNotFavorite(result);
    }

    @Override
    public void onClickFavorite(View view, Result result) {
        MobclickAgent.onEvent(this, "favorite_service");
        mPresenter.startFavoriteAnim(view, new BasePresenter.AnimationEndListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mPresenter.clickFavorite(view,result);
            }
        });
    }

    @Override
    public void onClickPlaySound(View view, Result result) {
        MobclickAgent.onEvent(this, "sound_service");
        mPresenter.playSound(result);
        mPresenter.startSoundAnim(view);
    }

    @Override
    public void onClickTipFrame(View view, Result result) {
        mPresenter.jumpMainActivity(result);
        removeTipView(result);
    }

    @Override
    public void onInitFavorite(ImageView mIvFavorite, Result result) {
        mPresenter.initFavoriteStatus(result);
    }

    @Override
    public void removeTipView(Result result) {
        mTipViewController.removeTipView(result);
    }

    @Override
    public void onRemove() {

    }

    public class ProcessBinder extends Binder {
        /**
         * 获取当前Service的实例
         * @return
         */
        public ListenClipboardService getService(){
            return ListenClipboardService.this;
        }
    }
}