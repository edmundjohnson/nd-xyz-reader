package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    /** Key for the shared transition name passed passed back from the detail activity. */
    public static final String KEY_SHARED_TRANSITION_NAME = "KEY_SHARED_TRANSITION_NAME";

    private static final String TAG = ArticleListActivity.class.toString();
    //private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private final SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private final GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    // The shared transition name passed back from the detail activity.
    private String mTransitionName;

    //---------------------------------------------------------------------------------------
    // Listeners

    private final SharedElementCallback mSharedElementCallback
            = new SharedElementCallback() {
        /**
         * Adjust the mapping of shared element names to Views.
         * Set the mapping to include only the shared elements for the selected page.
         * @param names The names of all shared elements transferred from the calling Activity
         *              or Fragment in the order they were provided.
         * @param sharedElements The mapping of shared element names to Views. The best guess
         *                       will be filled into sharedElements based on the transitionNames.
         */
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
//                if (mTmpReenterState != null) {
//                    int startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_ALBUM_POSITION);
//                    int currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_ALBUM_POSITION);
//                    if (startingPosition != currentPosition) {
//                        // If startingPosition != currentPosition the user must have swiped to a
//                        // different page in the detail activity. We must update the shared
//                        // element so that the correct one falls into place.
//                        String newTransitionName = ALBUM_NAMES[currentPosition];
//                        View newSharedElement = mRecyclerView.findViewWithTag(newTransitionName);
//                        if (newSharedElement != null) {
//                            names.clear();
//                            names.add(newTransitionName);
//                            sharedElements.clear();
//                            sharedElements.put(newTransitionName, newSharedElement);
//                        }
//                    }
//
//                    mTmpReenterState = null;
//                }
            if (mTransitionName != null) {
                View sharedElement = mRecyclerView.findViewWithTag(mTransitionName);
                if (sharedElement != null) {
                    names.clear();
                    names.add(mTransitionName);
                    sharedElements.clear();
                    sharedElements.put(mTransitionName, sharedElement);
                }
                mTransitionName = null;
            }
        }
    };

    //---------------------------------------------------------------------------------------
    // Activity lifecycle methods

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        //mToolbar = (Toolbar) findViewById(R.id.toolbar);
        //final View toolbarContainerView = findViewById(R.id.toolbar_container);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }

        setExitSharedElementCallback(mSharedElementCallback);
    }

    /**
     * Called when an activity you launched with an activity transition exposes this
     * Activity through a returning activity transition, giving you the resultCode
     * and any additional data from it. This method will only be called if the activity
     * set a result code other than {@link #RESULT_CANCELED} and it supports activity
     * transitions.
     * See:     https://github.com/alexjlockwood/adp-activity-transitions
     *
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     */
    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTransitionName = data.getStringExtra(KEY_SHARED_TRANSITION_NAME);

//            int currentPosition = RecyclerView.NO_POSITION;
//            if (mTransitionName != null) {
//                View sharedElement = mRecyclerView.findViewWithTag(mTransitionName);
//                // next line doesn't work - maybe pass position back from detail activity instead
//                currentPosition = mRecyclerView.getChildLayoutPosition(sharedElement);
//                mRecyclerView.scrollToPosition(currentPosition);
//            }

            postponeEnterTransition();
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public boolean onPreDraw() {
                    mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    // TODO: figure out why it is necessary to request layout here in order to get a smooth transition.
                    mRecyclerView.requestLayout();
                    startPostponedEnterTransition();
                    return true;
                }
            });
        }

    }

    //---------------------------------------------------------------------------------------
    // Refresh list methods

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    //---------------------------------------------------------------------------------------
    // Loader methods

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    //---------------------------------------------------------------------------------------
    // Adapter

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Uri uriDetail = ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition()));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uriDetail);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        intent.setAction(Intent.ACTION_VIEW);
                        Bundle bundle = ActivityOptions.makeSceneTransitionAnimation(
                                ArticleListActivity.this,
                                vh.thumbnailView,
                                vh.thumbnailView.getTransitionName()).toBundle();
                        ArticleListActivity.this.startActivity(intent, bundle);
                    } else {
                        startActivity(intent);
                    }
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            String displayDate;

            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                displayDate = DateUtils.getRelativeTimeSpanString(
                        publishedDate.getTime(),
                        System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_ALL).toString();
            } else {
                displayDate = outputFormat.format(publishedDate);
            }
            holder.subtitleView.setText(Html.fromHtml(
                    getString(R.string.article_subtitle_list_item,
                            displayDate,
                            getString(R.string.authored_by),
                            mCursor.getString(ArticleLoader.Query.AUTHOR))));

            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

            // Set the transition name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String transitionName = getString(R.string.transition_article)
                        + String.valueOf(mCursor.getLong(ArticleLoader.Query._ID));
                holder.thumbnailView.setTransitionName(transitionName);
                // Set the tag so the view can be found when the list activity is returned to.
                holder.thumbnailView.setTag(transitionName);
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final DynamicHeightNetworkImageView thumbnailView;
        public final TextView titleView;
        public final TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }
}
