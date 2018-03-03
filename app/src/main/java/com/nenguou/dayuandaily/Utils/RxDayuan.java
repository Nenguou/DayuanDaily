package com.nenguou.dayuandaily.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.nenguou.dayuandaily.BuildConfig;
import com.nenguou.dayuandaily.DataBase.DayuanDailyDatabase;
import com.nenguou.dayuandaily.Model.Captcha;
import com.nenguou.dayuandaily.Model.Class;
import com.nenguou.dayuandaily.Model.ClassName;
import com.nenguou.dayuandaily.Model.Grades;
import com.nenguou.dayuandaily.Model.Major;
import com.nenguou.dayuandaily.Model.YearCollege;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by binguner on 2018/2/15.
 */

public class RxDayuan {

    //private android.support.v7.app.AlertDialog alertDialog;
    //private android.support.v7.app.AlertDialog.Builder builder;

    private Context context;
    private String path;
    private Gson gson = new GsonBuilder()
            .setLenient()
            .create();

    private Retrofit retrofit = new Retrofit
            .Builder()
            .client(getNewClient(context))
            .baseUrl("https://ssl.liuyinxin.com/univ/api/schedule/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build();
    private DayuanService service = retrofit.create(DayuanService.class);
    private DayuanDailyDatabase dayuanDailyDatabase;
    private final String RxTag = "rxDayuanTag";

    private OkHttpClient getNewClient(Context mContext){
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        if(BuildConfig.DEBUG){
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        }else{
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        }
        path = "data/user/0/com.nenguou.dayuandaily/cache";
        File cacheFile = new File(path,"DayuanCache");
        final Cache cache = new Cache(cacheFile,10*1024*1024);
        // 缓存拦截器
        Interceptor cacheInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                if(!NetworkUtils.isAvailable(context)){
                    request = request.newBuilder()
                            .cacheControl(CacheControl.FORCE_CACHE)
                            .build();
                }

                Response response = chain.proceed(request);

                if (NetworkUtils.isAvailable(context)) {
                    int maxAge = 0;
                    response.newBuilder()
                            .header("Cache-Control", "public, only-if-cached, max-stale=" + maxAge)
                            .build();
                }
                return response;
            }
        };

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        builder.addInterceptor(loggingInterceptor)
                .addInterceptor(cacheInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15,TimeUnit.SECONDS)
                .cache(cache)
                .writeTimeout(15,TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        return builder.build();
    }

    public RxDayuan(Context context){
        this.context = context;
        dayuanDailyDatabase = DayuanDailyDatabase.getInstance(context);
        //initBd();
    }

//    private void initBd() {
//        builder = new android.support.v7.app.AlertDialog.Builder(context);
//        builder.setTitle("正在拼命加载");
//        builder.setMessage("请稍等");
//        builder.setCancelable(false);
//    }

    public void getGrades(final RetrofitCallbackListener listener){
        SharedPreferences sharedPreferences = context.getSharedPreferences("User_grades",Context.MODE_PRIVATE);
        String sessionId = sharedPreferences.getString("cookies","");
        service.getGrades(sessionId)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Grades>() {
                    @Override
                    public void onCompleted() {
                        listener.onFinish(0);
                    }

                    @Override
                    public void onError(Throwable e) {
                        listener.onFinish(1);
                        listener.onError((Exception) e);
                    }

                    @Override
                    public void onNext(Grades grades) {
                        if(grades!=null){
                            dayuanDailyDatabase.saveGrades(grades);
                        }
                    }
                });
    }

    public void getCaptcha(final RetrofitCallbackListener listener){
        service.getCaptcha()
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Captcha>() {
                    @Override
                    public void onCompleted() {
                        listener.onFinish(0);
                    }

                    @Override
                    public void onError(Throwable e) {
                        listener.onFinish(1);
                        listener.onError((Exception) e);
                    }

                    @Override
                    public void onNext(Captcha captcha) {
                        SharedPreferences.Editor editor = context.getSharedPreferences("User_grades",Context.MODE_PRIVATE).edit();
                        editor.putString("captchaUrl",captcha.getData().getCaptchaUrl());
                        editor.putString("cookies",captcha.getData().getCookies());
                        editor.commit();
                    }
                });
    }

    public void getLoginSuccess(final RetrofitCallbackListener listener){
        SharedPreferences sharedPreferences = context.getSharedPreferences("User_grades",Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username","");
        String password = sharedPreferences.getString("password","");
        String captcha = sharedPreferences.getString("captcha","");
        String sessionId = sharedPreferences.getString("cookies","");
        Log.d(RxTag,"username : "+ username + "  password :" + password + "captcha :" + captcha +"  sessionId : " + sessionId);
        try {
            service.login(username,password,captcha,sessionId)
                    .subscribeOn(Schedulers.io())
                    .unsubscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<String>() {
                        @Override
                        public void onCompleted() {
                            //Log.d(RxTag,"onCompleted  !");
                            listener.onFinish(0);
                        }

                        @Override
                        public void onError(Throwable e) {
                            //Log.d(RxTag,"onError  !"+e.toString());
                            listener.onFinish(1);
                            listener.onError((Exception) e);
                        }

                        @Override
                        public void onNext(String  s) {
                            s.trim();
                            //Log.d(RxTag,s.toString()+" ghjhgfgh !");
                        }
                    });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void getYearCollege(){
        service.getYearCollege()
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<YearCollege>() {
                    @Override
                    public void onCompleted() {
                        //alertDialog.dismiss();
                    }

                    @Override
                    public void onError(Throwable e) {
                        unsubscribe();
                    }

                    @Override
                    public void onNext(YearCollege collegesBean) {
                        if (collegesBean != null){
//                            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialogInterface, int i) {
//                                    onError(new Exception());
//                                }
//                            });
//                            alertDialog = builder.create();
//                            alertDialog.show();

                            dayuanDailyDatabase.saveYearCollege(collegesBean);
                            String term = collegesBean.getData().getTerms().get(0).getName();
                            SharedPreferences.Editor editor = context.getSharedPreferences("User_YearCollege",Context.MODE_PRIVATE).edit();
                            editor.putString("term",term);
                            editor.commit();
                        }
                    }
                });

    }

    public void getMajor(int collegeId){
        service.getMajor(collegeId)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Major>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Major major) {
                        //Log.d("DaYuanTag",major.getData().get(0).getMajor());
                        if(major!=null) {
                            dayuanDailyDatabase.saveMajor(major);
                        }
                    }
                });
    }

    public void getClassName(final int year, final String name){
        service.getClassName(year,name)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ClassName>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(ClassName className) {
                        //Log.d("DaYuanTag",className.getData().size()+"");
                        if(className != null) {
                            dayuanDailyDatabase.saveClassName(className, name, year);
                        }

                    }
                });
    }

    public void getClass(String name, String term){
        service.getClass(name,term)
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Class>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Class aClass) {
                        //Log.d("DaYuanTag1",aClass.getData().getData().toString());
                        if(aClass!=null) {

                            SharedPreferences.Editor editor = context.getSharedPreferences("User_YearCollege", Context.MODE_PRIVATE).edit();
                            editor.putInt("schedule_id", aClass.getData().getId());
                            editor.commit();
                            dayuanDailyDatabase.saveClass(aClass);
                        }
                    }
                });
    }



}