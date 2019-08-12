package cc.easyandroid.providers.core;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;

import java.io.File;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import cc.easyandroid.downloadprovider.BuildConfig;
import cc.easyandroid.providers.DownloadManager;
import cc.easyandroid.providers.downloads.Downloads;
import cc.easyandroid.providers.downloads.Helpers;
import okhttp3.OkHttpClient;

/**
 *
 */
public class EasyDownLoadManager extends Observable {
    protected Cursor mDownloadingCursor;
    protected HashMap<String, EasyDownLoadInfo> mDownloadingList;

    protected DownloadManager mDownloadManager;

    protected Context mContext;
    /**
     * This field should be made private, so it is hidden from the SDK.
     * {@hide}
     */
    protected ChangeObserver mChangeObserver;
    /**
     * This field should be made private, so it is hidden from the SDK.
     * {@hide}
     */
    protected DataSetObserver mDataSetObserver;

    protected DbStatusRefreshTask refreshTask;
    protected DownloadManager.Query baseQuery;

    /**
     * default constructor
     *
     * @param context
     */
    protected EasyDownLoadManager(Context context) {
        mContext = context;

        ContentResolver resolver = context.getContentResolver();
        mDownloadManager = new DownloadManager(resolver, context.getPackageName());
        mDownloadingList = new HashMap<>();
        mChangeObserver = new ChangeObserver();
        mDataSetObserver = new MyDataSetObserver();

        refreshTask = new DbStatusRefreshTask(resolver);
        baseQuery = onCreatQuery();


        startQuery();
        Helpers.getAsyncHandler().post(new Runnable() {
            @Override
            public void run() {
                Helpers.handleBootCompleted(mContext);
            }
        });
    }

    protected DownloadManager.Query onCreatQuery() {
        return new DownloadManager.Query().orderBy(DownloadManager.COLUMN_ID, DownloadManager.Query.ORDER_ASCENDING);
    }

    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    private static EasyDownLoadManager mInstance;

    public static synchronized EasyDownLoadManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new EasyDownLoadManager(context);
        }
        return mInstance;
    }

    @Override
    public void addObserver(Observer observer) {
        super.addObserver(observer);
        if (mDownloadingList != null) {
            observer.update(this, mDownloadingList);
        }
    }

    private void startQuery() {
        mDownloadManager.query(refreshTask, DbStatusRefreshTask.DOWNLOAD, null, baseQuery);//异步查询

    }


    /*
     * 刷新正在下载中的应用
     */
    private void refreshDownloadApp(Cursor cursor) {
        mDownloadingList = new HashMap<>();
        if (cursor.getCount() <= 0) {
            return;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            //System.out.println("cgp=cursor.cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()() ＝"+cursor.getCount() );
            //获取index
            int mIdColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);//id
            int mStatusColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS); //status
            int mTotalBytesColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);//总大小
            int mCurrentBytesColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);//下载大小
            int mLocalUriId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI);//本地路径  apkpath
            int mTitleColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE); //title
            int mURIColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI);//下载diz

            EasyDownLoadInfo infoItem = new EasyDownLoadInfo();
            //获取值
            infoItem.setId(cursor.getInt(mIdColumnId));
            infoItem.setStatus(cursor.getInt(mStatusColumnId));
            infoItem.setTotalBytes(cursor.getLong(mTotalBytesColumnId));
            infoItem.setCurrentBytes(cursor.getLong(mCurrentBytesColumnId));
            infoItem.setLocal_uri(cursor.getString(mLocalUriId));
            infoItem.setTitle(cursor.getString(mTitleColumnId));
            infoItem.setUri(cursor.getString(mURIColumnId));
            //System.out.println("cgp--down--" + infoItem.getUri() + infoItem.getTitle() + "---" + infoItem.getLocal_uri());
            mDownloadingList.put(infoItem.getUri() + infoItem.getTitle(), infoItem);
            if (DownloadManager.isStatusRunning(infoItem.getStatus())) {//正在下载
            } else if (DownloadManager.isStatusPending(infoItem.getStatus())) {//等待
                // 下载等待中
            } else if (infoItem.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {// 下载完成
                // download success
                // 检查文件完整性，如果不存在，删除此条记录
                if (!new File(infoItem.getLocal_uri()).exists()) {
//                    mDownloadingList.remove(infoItem.getUri());
                }
            } else if (infoItem.getStatus() == DownloadManager.STATUS_PAUSED) {// 暂停 //

            } else {
//                System.out.println("下载出错");
            }
        }
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     *
     * @param cursor The new cursor to be used
     */
    private void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there wasa not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    private Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mDownloadingCursor) {
            return null;
        }
        Cursor oldCursor = mDownloadingCursor;
        if (oldCursor != null) {
            if (mChangeObserver != null) {
                oldCursor.unregisterContentObserver(mChangeObserver);
            }
            if (mDataSetObserver != null) {
                oldCursor.unregisterDataSetObserver(mDataSetObserver);
            }
        }
        mDownloadingCursor = newCursor;
        if (newCursor != null) {
            if (mChangeObserver != null) {
                newCursor.registerContentObserver(mChangeObserver);
            }
            if (mDataSetObserver != null) {
                newCursor.registerDataSetObserver(mDataSetObserver);
            }
            // notify the observers about the new cursor
            refreshDownloadApp(newCursor);
            notifyDataSetChanged();
        } else {
            // notify the observers about the lack of a data set
            notifyDataSetInvalidated();
        }
        return oldCursor;
    }

    /**
     * 本地数据库刷新检查
     */
    private class DbStatusRefreshTask extends AsyncQueryHandler {

        private final static int DOWNLOAD = 0; //token
        private final static int UPDATE = 1;

        public DbStatusRefreshTask(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

            switch (token) {
                case DOWNLOAD:
                    if (cursor != null) {
                        changeCursor(new DownloadManager.CursorTranslator(cursor, Downloads.CONTENT_URI));
                    }
                    break;
                default:
                    break;
            }
        }
    }


    private void requeryCursor() {
        startQuery();
    }

    public void close() {
        if (mDownloadingCursor != null) {
            if (mChangeObserver != null) {
                mDownloadingCursor.unregisterContentObserver(mChangeObserver);
                mChangeObserver = null;
            }
            if (mDataSetObserver != null) {
                mDownloadingCursor.unregisterDataSetObserver(mDataSetObserver);
                mDataSetObserver = null;
            }
            mDownloadingCursor.close();
            mDownloadingCursor = null;
        }
        deleteObservers();
        mInstance = null;
    }

    public static void destroy() {
        if (mInstance != null) {
            mInstance.close();
        }
    }

    /**
     * 通知被观察者
     */
    private void notifyDataSetChanged() {
        setChanged();
        notifyObservers(mDownloadingList);
    }

    private void notifyDataSetInvalidated() {//初始化数据
        mDownloadingList = new HashMap<>();

    }

    private class ChangeObserver extends ContentObserver {
        public ChangeObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            requeryCursor();
        }
    }

    private class MyDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            requeryCursor();
        }

        @Override
        public void onInvalidated() {
            notifyDataSetInvalidated();
        }
    }

    private OkHttpClient mOkHttpClient;

    public void setOkHttpClient(OkHttpClient okHttpClient) {
        this.mOkHttpClient = okHttpClient;
    }

    public OkHttpClient getOkHttpClient() {
        if (mOkHttpClient == null) {
            synchronized (EasyDownLoadManager.class) {
                if (mOkHttpClient == null) {
                    mOkHttpClient = new OkHttpClient();
                }
            }
        }
        return mOkHttpClient;
    }
}
