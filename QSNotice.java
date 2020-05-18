package com.qinsong.library;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by song on 2016/9/13.
 * update 2020-5-5
 * ver 2.0
 * 事件即时通知,不同界面更新数据,支持注解
 * TODO register和remove必须成对出现
 */
public class QSNotice {

    private static Map<String, List<Notice>> map = new HashMap<>();
    private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();//读写锁并发安全问题(主要是maplist遍历时被其他线程改变导致出错

    /**
     * 注册通知
     *
     * @param notice  订阅类
     * @param actions 标记key
     */
    public static void registerNotice(Notice notice, String... actions) {
        try {
            lock.writeLock().lock();
            for (String action : actions) {
                List<Notice> l = map.get(action);
                if (l == null)
                    l = new ArrayList<>();
                l.add(notice);
                map.put(action, l);
                Log.i("NoticeManager", "registerNotice:" + notice + "->" + action);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 移除通知,必须和注册成对,否则内存泄漏
     *
     * @param notice  订阅类
     * @param actions 标记key,空则移除该订阅类所有注册的action
     */
    public static void removeNotice(Notice notice, String... actions) {
        try {
            lock.writeLock().lock();
            if (actions == null || actions.length == 0) {
                Set<String> strings = map.keySet();
                actions = new String[strings.size()];
                int i = 0;
                for (String s : strings) {
                    actions[i] = s;
                    i++;
                }
            }
            for (String action : actions) {
                List<Notice> l = map.get(action);
                if (l == null)
                    continue;
                Iterator<Notice> iterList = l.iterator();//List接口实现了Iterable接口
                while (iterList.hasNext()) {
                    Notice notice1 = iterList.next();
                    if (notice1 == null || notice1 == notice) {
                        iterList.remove();
                        Log.i("NoticeManager", "removeNotice:" + notice1 + "->" + action);
                    }
                }
                if (l.size() == 0)
                    map.remove(action);
            }
        } finally {
            lock.writeLock().unlock();

        }
    }


    /**
     * 发送通知
     *
     * @param action 通知标记
     * @param event  通知内容
     */
    public static void sendNotice(String action, Object event) {
        send(action, event);
    }

    /**
     * 发送空通知
     *
     * @param action 通知标记
     */
    public static void sendEmptyNotice(String action) {
        send(action, null);
    }

    public static void sendNoticeToUI(String action, Object o) {
        handler.obtainMessage(0, new Object[]{action, o}).sendToTarget();
    }

    private static Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            Object[] o = (Object[]) message.obj;
            send((String) o[0], o[1]);
        }
    };

    private static void send(String action, Object event) {
        try {
            lock.readLock().lock();
            List<Notice> l = map.get(action);
            if (l == null)
                return;
            for (int i = 0; i < l.size(); i++) {
                l.get(i).onNotice(action, event);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public interface Notice {
        void onNotice(String action, Object event);
    }

    //======================================注解支持==========================================

    private static Map<Object, List<Notice>> mapA = new HashMap<>();
    private static ReentrantReadWriteLock lockA = new ReentrantReadWriteLock();

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface Action {
        //action,空取参数名
        String value() default "";
    }

    /**
     * 注册注解@Action的通知,基于反射效率没有registerNotice高
     *
     * @param subscriber 注解类
     */
    public static void registerAction(final Object subscriber) {
        try {
            lockA.writeLock().lock();
            Class<?> c = subscriber.getClass();
            Method[] methods = c.getDeclaredMethods();
            for (final Method m : methods) {
                Action action = m.getAnnotation(Action.class);
                if (action != null) {//发现注解

                    String actionStr = action.value();//优先使用显式action
                    boolean isHaveParam = false;

                    Type[] types = m.getGenericParameterTypes();//获取方法入参
                    if (types.length > 1) {
                        throw new IllegalArgumentException("订阅方法不支持多个参数->" + m);
                    } else if (types.length == 1) {
                        if (actionStr.isEmpty()) {
                            Type type = types[0];
                            if (type instanceof ParameterizedType) {
                                throw new IllegalArgumentException("订阅事件不支持泛型入参,请声明Action(\"xxxx\")->" + m);
                            }
//                            if (type instanceof CharSequence) {
//                                throw new IllegalArgumentException("订阅事件不支持字符入参->" + m);
//                            }
                            actionStr = ((Class) type).getName();
                        }
                        isHaveParam = true;
                    } else {
                        if (actionStr.isEmpty())
                            throw new IllegalArgumentException("订阅方法必须声明Action(\"xxx\")或有一个入参->" + m);
                    }
                    //注册通知
                    final boolean finalIsHaveParam = isHaveParam;
                    Notice notice = new Notice() {
                        @Override
                        public void onNotice(String action, Object o) {
                            try {
                                m.invoke(subscriber, finalIsHaveParam ? o : null);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e("NoticeManager", "sendNotice_Error->" + m);
                            }
                        }
                    };
                    registerNotice(notice, actionStr);
                    List<Notice> l = mapA.get(subscriber);
                    if (l == null) l = new ArrayList<>();
                    l.add(notice);
                    mapA.put(subscriber, l);
                }
            }
        } finally {
            lockA.writeLock().unlock();
        }
    }

    /**
     * 移除注解通知
     *
     * @param subscriber 注解类
     */
    public static void removeAction(final Object subscriber) {
        try {
            lockA.writeLock().lock();
            if (subscriber instanceof Notice)//顺便移除
                removeNotice((Notice) subscriber);
            List<Notice> l = mapA.remove(subscriber);
            if (l != null)
                for (Notice n : l)
                    removeNotice(n);
        } finally {
            lockA.writeLock().unlock();
        }
    }

    /**
     * 发送事件
     *
     * @param event 事件对象
     */
    public static void sendNotice(Object event) {
        sendNotice(event.getClass().getName(), event);
    }

    public static void sendNoticeToUI(Object event) {
        sendNoticeToUI(event.getClass().getName(), event);
    }

}
