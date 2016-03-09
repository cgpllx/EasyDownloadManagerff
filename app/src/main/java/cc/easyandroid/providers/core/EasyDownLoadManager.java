package cc.easyandroid.providers.core;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;

import cc.easyandroid.providers.DownloadManager;
import cc.easyandroid.providers.downloads.Downloads;

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

    /**
     * default constructor
     *
     * @param context
     */
    private EasyDownLoadManager(Context context) {
        synchronized (this) {
            mContext = context;
            mDownloadManager = new DownloadManager(context.getContentResolver(), context.getPackageName());
            mDownloadingList = new HashMap<String, EasyDownLoadInfo>();
            mChangeObserver = new ChangeObserver();
            mDataSetObserver = new MyDataSetObserver();
            startQuery();
        }
    }

    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    private static EasyDownLoadManager mInstance;

    public static EasyDownLoadManager open(Context context) {
        if (mInstance == null) {
            mInstance = new EasyDownLoadManager(context);
        }
        return mInstance;
    }


    private void startQuery() {
        System.out.println("cgp=startQuery"+111);
        DbStatusRefreshTask refreshTask = new DbStatusRefreshTask(mContext.getContentResolver());
        DownloadManager.Query baseQuery = new DownloadManager.Query();
        mDownloadManager.query(refreshTask, DbStatusRefreshTask.DOWNLOAD, null, baseQuery);
    }


    /*
    * 刷新正在下载中的应用
    */
    private void refreshDownloadApp(Cursor cursor) {
//System.out.println("cgp=refreshDownloadApp"+cursor);
        if (cursor.getCount() > 0) {
            // 检索有结果
            mDownloadingList = new HashMap<String, EasyDownLoadInfo>();
            System.out.println("cgp=cursor.getCount() ＝"+cursor.getCount() );
        } else {
            System.out.println("cgp=cursor.getCount() ＝"+cursor.getCount() );
            return;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
//            System.out.println("cgp=cursor.cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()() ＝"+cursor.getCount() );
            //获取index
            int mIdColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);//id
            int mStatusColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS); //status
            int mTotalBytesColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);//总大小
            int mCurrentBytesColumnId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR );//下载大小
            int mLocalUriId = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI );//本地路径  apkpath
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

            mDownloadingList.put(infoItem.getUri()+infoItem.getTitle(), infoItem);
            System.out.println("cgp=refreshDownloadApp="+infoItem.getCurrentBytes());
            System.out.println("cgp=refreshDownloadApp总大笑="+infoItem.getTotalBytes());
            if (DownloadManager.isStatusRunning(infoItem.getStatus())) {//正在下载
                // downloading progress
                System.out.println("cgp=refreshDownloadApp="+infoItem.getCurrentBytes());


            } else if (DownloadManager.isStatusPending(infoItem.getStatus())) {//等待
                // 下载等待中

            } else if (infoItem.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {// 下载完成
                // download success
                // 检查文件完整性，如果不存在，删除此条记录
                if (!new File(infoItem.getLocal_uri()).exists()) {
//                    mDownloadingList.remove(infoItem.getUri());
                }
            } else if (infoItem.getStatus() == DownloadManager.STATUS_PAUSED) {// 暂停 //

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
            if (mChangeObserver != null) oldCursor.unregisterContentObserver(mChangeObserver);
            if (mDataSetObserver != null) oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mDownloadingCursor = newCursor;
        if (newCursor != null) {
            if (mChangeObserver != null) newCursor.registerContentObserver(mChangeObserver);
            if (mDataSetObserver != null) newCursor.registerDataSetObserver(mDataSetObserver);
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

        private final static int DOWNLOAD = 0;
        private final static int UPDATE = 1;

        public DbStatusRefreshTask(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

            switch (token) {
                case DOWNLOAD:
                    System.out.println("cgp=onQueryComplete"+111);

                    changeCursor( new DownloadManager.CursorTranslator(cursor, Downloads.CONTENT_URI));
                    break;
                default:
                    break;
            }
        }
    }


    private void requeryCursor() {
//        mDownloadingCursor.requery();
        startQuery();
        synchronized (this) {
//            refreshDownloadApp(mDownloadingCursor);
        }
    }

    public void close() {
        if (mDownloadingCursor != null) {
            if (mChangeObserver != null)
                mDownloadingCursor.unregisterContentObserver(mChangeObserver);
            if (mDataSetObserver != null)
                mDownloadingCursor.unregisterDataSetObserver(mDataSetObserver);
            mDownloadingCursor.close();
        }
        mInstance = null;
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
}