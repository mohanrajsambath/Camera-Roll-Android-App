package us.koller.cameraroll.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import us.koller.cameraroll.R;
import us.koller.cameraroll.adapter.fileExplorer.RecyclerViewAdapter;
import us.koller.cameraroll.data.FileOperations.FileOperation;
import us.koller.cameraroll.data.FileOperations.Copy;
import us.koller.cameraroll.data.FileOperations.Delete;
import us.koller.cameraroll.data.FileOperations.Move;
import us.koller.cameraroll.data.File_POJO;
import us.koller.cameraroll.data.Provider.FilesProvider;
import us.koller.cameraroll.data.Provider.Provider;
import us.koller.cameraroll.data.StorageRoot;
import us.koller.cameraroll.ui.widget.ParallaxImageView;
import us.koller.cameraroll.ui.widget.SwipeBackCoordinatorLayout;
import us.koller.cameraroll.util.ColorFade;
import us.koller.cameraroll.util.Util;

public class FileExplorerActivity extends AppCompatActivity
        implements SwipeBackCoordinatorLayout.OnSwipeListener, RecyclerViewAdapter.Callback {

    public interface OnDirectoryChangeCallback {
        void changeDir(String path);
    }

    public static final String ROOTS = "ROOTS";
    public static final String CURRENT_DIR = "CURRENT_DIR";
    public static final String SELECTED_ITEMS = "SELECTED_ITEMS";
    public static final String STORAGE_ROOTS = "Storage Roots";

    private File_POJO roots;

    private File_POJO currentDir;

    private FilesProvider filesProvider;

    private RecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    private Menu menu;

    private FileOperation fileOperation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_explorer);

        currentDir = new File_POJO("", false);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.black_translucent2));
        toolbar.setNavigationIcon(AnimatedVectorDrawableCompat
                .create(this, R.drawable.back_to_cancel_animateable));
        setSupportActionBar(toolbar);

        /*//set Toolbar overflow icon color
        Drawable drawable = toolbar.getOverflowIcon();
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable.mutate(),
                    ContextCompat.getColor(this, R.color.grey_900_translucent));
            toolbar.setOverflowIcon(drawable);
        }*/
        Util.colorToolbarOverflowMenuIcon(toolbar,
                ContextCompat.getColor(this, R.color.white_translucent1));

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.file_explorer));
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final ViewGroup rootView = (ViewGroup) findViewById(R.id.swipeBackView);
        if (rootView instanceof SwipeBackCoordinatorLayout) {
            ((SwipeBackCoordinatorLayout) rootView).setOnSwipeListener(this);
        }

        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setTag(ParallaxImageView.RECYCLER_VIEW_TAG);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAdapter = new RecyclerViewAdapter(
                new OnDirectoryChangeCallback() {
                    @Override
                    public void changeDir(String path) {
                        loadDirectory(path);
                    }
                }, this);
        if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_DIR)) {
            recyclerViewAdapter.setFiles(currentDir);
        }
        recyclerViewAdapter.notifyDataSetChanged();
        recyclerView.setAdapter(recyclerViewAdapter);

        //setting window insets manually
        rootView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                toolbar.setPadding(toolbar.getPaddingStart() /*+ insets.getSystemWindowInsetLeft()*/,
                        toolbar.getPaddingTop() + insets.getSystemWindowInsetTop(),
                        toolbar.getPaddingEnd() /*+ insets.getSystemWindowInsetRight()*/,
                        toolbar.getPaddingBottom());

                ViewGroup.MarginLayoutParams toolbarParams
                        = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
                toolbarParams.leftMargin += insets.getSystemWindowInsetLeft();
                toolbarParams.rightMargin += insets.getSystemWindowInsetRight();
                toolbar.setLayoutParams(toolbarParams);

                recyclerView.setPadding(recyclerView.getPaddingStart() + insets.getSystemWindowInsetLeft(),
                        recyclerView.getPaddingTop() /*+ insets.getSystemWindowInsetTop()*/,
                        recyclerView.getPaddingEnd() + insets.getSystemWindowInsetRight(),
                        recyclerView.getPaddingBottom() + insets.getSystemWindowInsetBottom());

                // clear this listener so insets aren't re-applied
                rootView.setOnApplyWindowInsetsListener(null);
                return insets.consumeSystemWindowInsets();
            }
        });

        //setting recyclerView top padding, so recyclerView starts below the toolbar
        toolbar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                recyclerView.setPadding(recyclerView.getPaddingStart(),
                        recyclerView.getPaddingTop() + toolbar.getHeight(),
                        recyclerView.getPaddingEnd(),
                        recyclerView.getPaddingBottom());

                recyclerView.scrollToPosition(0);

                toolbar.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });

        //needed to achieve transparent navBar
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        //load files
        if (savedInstanceState != null
                && savedInstanceState.containsKey(CURRENT_DIR)
                && savedInstanceState.containsKey(ROOTS)) {
            roots = savedInstanceState.getParcelable(ROOTS);
            currentDir = savedInstanceState.getParcelable(CURRENT_DIR);

            recyclerViewAdapter.setFiles(currentDir);
            recyclerViewAdapter.notifyDataSetChanged();
            onDataChanged();

            /*if(savedInstanceState.containsKey(SELECTED_ITEMS)) {
                File_POJO[] selectedItems
                        = (File_POJO[]) savedInstanceState.getParcelableArray(SELECTED_ITEMS);
                if(selectedItems != null) {
                    recyclerViewAdapter.enterSelectorMode(selectedItems);
                }
            }*/
        } else {
            loadRoots();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.warning)
                .setMessage(Html.fromHtml(getString(R.string.file_explorer_warning_message)))
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })
                .show();
    }

    public void loadRoots() {
        StorageRoot[] storageRoots = FilesProvider.getRoots(this);
        roots = new StorageRoot(STORAGE_ROOTS);
        for (int i = 0; i < storageRoots.length; i++) {
            roots.addChild(storageRoots[i]);
        }

        FileExplorerActivity.this.currentDir = roots;
        if (recyclerViewAdapter != null) {
            recyclerViewAdapter.setFiles(currentDir);
            recyclerViewAdapter.notifyDataSetChanged();
            onDataChanged();
        }
    }

    public void loadDirectory(final String path) {
        Log.d("FileExplorerActivity", "loadDirectory(): " + path);
        final Snackbar snackbar = Snackbar.make(findViewById(R.id.root_view),
                getString(R.string.loading), Snackbar.LENGTH_INDEFINITE);
        Util.showSnackbar(snackbar);

        final FilesProvider.Callback callback = new FilesProvider.Callback() {
            @Override
            public void onDirLoaded(final File_POJO dir) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        filesProvider.onDestroy();
                        filesProvider = null;

                        if (dir != null) {
                            FileExplorerActivity.this.currentDir = dir;
                            if (recyclerViewAdapter != null) {
                                recyclerViewAdapter.setFiles(currentDir);
                                recyclerViewAdapter.notifyDataSetChanged();
                                onDataChanged();
                            }
                        }

                        snackbar.dismiss();
                    }
                });
            }

            @Override
            public void timeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        snackbar.dismiss();

                        final Snackbar snackbar = Snackbar.make(findViewById(R.id.root_view),
                                R.string.loading_failed, Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(getString(R.string.retry), new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                loadDirectory(path);
                            }
                        });
                        Util.showSnackbar(snackbar);
                    }
                });
            }

            @Override
            public void needPermission() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        snackbar.dismiss();
                    }
                });
            }
        };

        filesProvider = new FilesProvider(this);
        filesProvider.loadDir(this, path, callback);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ROOTS, roots);
        if (currentDir != null) {
            outState.putParcelable(CURRENT_DIR, currentDir);
        }

        File_POJO[] selectedItems = recyclerViewAdapter.getSelectedItems();
        if (selectedItems.length > 0) {
            outState.putParcelableArray(SELECTED_ITEMS, selectedItems);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_explorer, menu);
        this.menu = menu;
        //hide menu items; items are made visible, when a folder gets selected
        manageMenuItems();

        Drawable icon = menu.findItem(R.id.paste).getIcon().mutate();
        icon.setTint(ContextCompat.getColor(FileExplorerActivity.this,
                R.color.grey_900_translucent));
        menu.findItem(R.id.paste).setIcon(icon);

        return super.onCreateOptionsMenu(menu);
    }

    public void manageMenuItems() {
        if (menu != null) {
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setVisible(false);

                int id = menu.getItem(i).getItemId();
                if (id == R.id.exclude) {
                    if (currentDir != null) {
                        menu.getItem(i).setVisible(!isCurrentFileARoot());
                        if (Provider.isPathPermanentlyExcluded(currentDir.getPath())) {
                            menu.getItem(i).setChecked(true);
                            menu.getItem(i).setEnabled(false);
                        } else {
                            menu.getItem(i).setChecked(!isCurrentFileARoot() && currentDir.excluded);
                            menu.getItem(i).setEnabled(!isCurrentFileARoot()
                                    && !Provider.isDirExcludedBecauseParentDirIsExcluded(
                                    currentDir.getPath(), Provider.getExcludedPaths()));
                        }
                    } else {
                        menu.getItem(i).setVisible(true);
                        menu.getItem(i).setChecked(false);
                        menu.getItem(i).setEnabled(false);
                    }
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (recyclerViewAdapter.isModeActive()
                        || recyclerViewAdapter.getMode()
                        == RecyclerViewAdapter.PICK_TARGET_MODE) {
                    FileOperation.operation = FileOperation.EMPTY;
                    recyclerViewAdapter.cancelMode();
                } else {
                    onBackPressed();
                }
                break;
            case R.id.exclude:
                currentDir.excluded = !currentDir.excluded;
                Log.d("FileExplorerActivity", "onOptionsItemSelected: " + currentDir.getPath()
                        + "; " + String.valueOf(currentDir.excluded));
                item.setChecked(currentDir.excluded);
                if (currentDir.excluded) {
                    FilesProvider.addExcludedPath(this, currentDir.getPath());
                } else {
                    FilesProvider.removeExcludedPath(this, currentDir.getPath());
                }
                break;
            case R.id.paste:
                if (!currentDir.getPath().equals(STORAGE_ROOTS)) {
                    recyclerViewAdapter.cancelMode();
                    if (fileOperation != null) {
                        fileOperation.execute(this,
                                recyclerViewAdapter.getFiles(),
                                new FileOperation.Callback() {
                                    @Override
                                    public void done() {
                                        loadDirectory(currentDir.getPath());
                                    }

                                    @Override
                                    public void failed(String path) {

                                    }
                                });
                    }
                } else {
                    Toast.makeText(this, "You can't "
                            + FileOperation.getModeString(this)
                            + " files here!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.move:
                FileOperation.operation = FileOperation.MOVE;
                recyclerViewAdapter.cancelMode();
                break;
            case R.id.copy:
                FileOperation.operation = FileOperation.COPY;
                recyclerViewAdapter.cancelMode();
                break;
            case R.id.delete:
                FileOperation.operation = FileOperation.DELETE;
                recyclerViewAdapter.cancelMode();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (recyclerViewAdapter.isModeActive()) {
            recyclerViewAdapter.cancelMode();
        } else if (currentDir != null && !currentDir.getPath().equals(STORAGE_ROOTS)) {
            if (!isCurrentFileARoot()) {
                String path = currentDir.getPath();
                int index = path.lastIndexOf("/");
                String parentPath = path.substring(0, index);

                loadDirectory(parentPath);
            } else {
                loadRoots();
            }
        } else {
            setResult(RESULT_OK, new Intent(MainActivity.REFRESH_MEDIA));
            super.onBackPressed();
        }
    }

    private boolean isCurrentFileARoot() {
        if (currentDir != null) {
            if (currentDir.getPath().equals(STORAGE_ROOTS)) {
                return true;
            }

            for (int i = 0; i < roots.getChildren().size(); i++) {
                if (currentDir.getPath().equals(roots.getChildren().get(i).getPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Provider.saveExcludedPaths(this);

        if (filesProvider != null) {
            filesProvider.onDestroy();
        }
    }

    @Override
    public boolean canSwipeBack(int dir) {
        return SwipeBackCoordinatorLayout.canSwipeBackForThisView(recyclerView, dir);
    }

    @Override
    public void onSwipeProcess(float percent) {
        getWindow().getDecorView().setBackgroundColor(SwipeBackCoordinatorLayout.getBackgroundColor(percent));
    }

    @Override
    public void onSwipeFinish(int dir) {
        getWindow().setReturnTransition(new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(new Slide(dir > 0 ? Gravity.TOP : Gravity.BOTTOM))
                .addTransition(new Fade())
                .setInterpolator(new AccelerateDecelerateInterpolator()));
        this.finish();
    }

    @Override
    public void onSelectorModeEnter() {
        fileOperation = null;
        FileOperation.operation = FileOperation.EMPTY;

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.transparent));
        toolbar.setActivated(true);
        toolbar.animate().translationY(0.0f).start();

        Util.setDarkStatusBarIcons(findViewById(R.id.root_view));

        Util.colorToolbarOverflowMenuIcon(toolbar,
                ContextCompat.getColor(FileExplorerActivity.this, R.color.grey_900_translucent));

        ColorFade.fadeBackgroundColor(toolbar,
                ContextCompat.getColor(this, R.color.black_translucent2),
                ContextCompat.getColor(this, R.color.colorAccent));

        ((Animatable) toolbar.getNavigationIcon()).start();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                toolbar.setNavigationIcon(AnimatedVectorDrawableCompat
                        .create(FileExplorerActivity.this, R.drawable.cancel_to_back_vector_animateable));

                //make menu items visible
                for (int i = 0; i < menu.size(); i++) {
                    int id = menu.getItem(i).getItemId();
                    if (id == R.id.paste || id == R.id.exclude) {
                        menu.getItem(i).setVisible(false);
                    } else {
                        menu.getItem(i).setVisible(true);
                    }
                }
            }
        }, 300);
    }

    @Override
    public void onSelectorModeExit(File_POJO[] selected_items) {
        switch (FileOperation.operation) {
            case FileOperation.DELETE:
                resetToolbar();
                fileOperation = new Delete(selected_items);
                fileOperation.execute(this, null,
                        new FileOperation.Callback() {
                            @Override
                            public void done() {
                                loadDirectory(currentDir.getPath());
                            }

                            @Override
                            public void failed(String path) {

                            }
                        });
                break;
            case FileOperation.COPY:
                fileOperation = new Copy(selected_items);
                recyclerViewAdapter.pickTarget();
                break;
            case FileOperation.MOVE:
                fileOperation = new Move(selected_items);
                recyclerViewAdapter.pickTarget();
                break;
        }

        if (fileOperation == null) {
            resetToolbar();
        }
    }

    @Override
    public void onItemSelected(int count) {
        if (count != 0) {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            final String title = String.valueOf(count) + (count > 1 ?
                    getString(R.string.items) : getString(R.string.item));

            int color = ContextCompat.getColor(this, R.color.grey_900_translucent);
            ColorFade.fadeToolbarTitleColor(toolbar, color,
                    new ColorFade.ToolbarTitleFadeCallback() {
                        @Override
                        public void setTitle(Toolbar toolbar) {
                            toolbar.setTitle(title);
                        }
                    }, true);
        }
    }

    @Override
    public void onPickTargetModeEnter() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (fileOperation != null) {
            final int count = fileOperation.getFiles().length;

            int color = ContextCompat.getColor(this, R.color.grey_900_translucent);
            ColorFade.fadeToolbarTitleColor(toolbar, color,
                    new ColorFade.ToolbarTitleFadeCallback() {
                        @Override
                        public void setTitle(Toolbar toolbar) {
                            toolbar.setTitle(FileOperation.getModeString(FileExplorerActivity.this) + " "
                                    + String.valueOf(count)
                                    + (count > 1 ? getString(R.string.items) : getString(R.string.item)));
                        }
                    }, true);
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                //hide menu items
                for (int i = 0; i < menu.size(); i++) {
                    int id = menu.getItem(i).getItemId();
                    if (id == R.id.paste) {
                        menu.getItem(i).setVisible(true);
                    } else {
                        menu.getItem(i).setVisible(false);
                    }
                }
            }
        }, 300);
    }

    @Override
    public void onPickTargetModeExit() {
        resetToolbar();
    }

    @Override
    public void onDataChanged() {
        final TextView emptyState = (TextView) findViewById(R.id.empty_state);
        emptyState.animate()
                .alpha(currentDir.getChildren().size() == 0 ? 1.0f : 0.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        emptyState.setVisibility(
                                currentDir.getChildren().size() == 0 ?
                                        View.VISIBLE : View.GONE);
                    }
                })
                .setDuration(100)
                .start();

        if (recyclerViewAdapter.getMode() == RecyclerViewAdapter.NORMAL_MODE) {
            final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

            int color = ContextCompat.getColor(FileExplorerActivity.this, R.color.white);
            ColorFade.fadeToolbarTitleColor(toolbar, color,
                    new ColorFade.ToolbarTitleFadeCallback() {
                        @Override
                        public void setTitle(Toolbar toolbar) {
                            toolbar.setTitle(currentDir.getPath());
                        }
                    }, true);
        }

        if (recyclerViewAdapter.getMode()
                == RecyclerViewAdapter.NORMAL_MODE) {
            manageMenuItems();
        }
    }

    public void resetToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.transparent));

        int color = ContextCompat.getColor(FileExplorerActivity.this, R.color.white);
        ColorFade.fadeToolbarTitleColor(toolbar, color,
                new ColorFade.ToolbarTitleFadeCallback() {
                    @Override
                    public void setTitle(Toolbar toolbar) {
                        toolbar.setTitle(currentDir.getPath());
                    }
                }, false);

        toolbar.setActivated(false);
        ColorFade.fadeBackgroundColor(toolbar,
                ContextCompat.getColor(this, R.color.colorAccent),
                ContextCompat.getColor(this, R.color.black_translucent2));

        ((Animatable) toolbar.getNavigationIcon()).start();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                toolbar.setNavigationIcon(AnimatedVectorDrawableCompat
                        .create(FileExplorerActivity.this, R.drawable.back_to_cancel_animateable));

                Util.setLightStatusBarIcons(findViewById(R.id.root_view));

                Util.colorToolbarOverflowMenuIcon(toolbar,
                        ContextCompat.getColor(FileExplorerActivity.this, R.color.white_translucent1));

                //hide menu items
                for (int i = 0; i < menu.size(); i++) {
                    //menu.getItem(i).setVisible(false);
                    int id = menu.getItem(i).getItemId();
                    if (id == R.id.exclude) {
                        menu.getItem(i).setVisible(true);
                    } else {
                        menu.getItem(i).setVisible(false);
                    }
                }
            }
        }, 300);
    }

    /*public static class FileAction {

        public interface Callback {
            void done();
        }

        static final int EMPTY = 0;
        static final int MOVE = 1;
        static final int COPY = 2;
        static final int DELETE = 3;

        static int action = EMPTY;

        private File_POJO[] files;

        FileAction(File_POJO[] files) {
            this.files = files;
        }

        File_POJO[] getFiles() {
            return files;
        }

        void execute(final Activity context, final File_POJO target, final Callback callback) {

            if ((FileAction.action == FileAction.EMPTY)
                    || (target == null && action == FileAction.MOVE)
                    || (target == null && action == FileAction.COPY)) {
                return;
            }

            if (FileAction.action == FileAction.MOVE) {
                if (target == null) {
                    return;
                }

                int success_count = 0;
                for (int i = 0; i < files.length; i++) {
                    boolean result = moveFile(files[i].getPath(), target.getPath());
                    success_count += result ? 1 : 0;
                    if (result) {
                        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.parse(files[i].getPath())));
                        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.parse(target.getPath())));
                    }
                }

                Toast.makeText(context, context.getString(R.string.successfully_moved)
                        + String.valueOf(success_count) + "/"
                        + String.valueOf(files.length), Toast.LENGTH_SHORT).show();

            } else if (FileAction.action == FileAction.COPY) {
                if (target == null) {
                    return;
                }

                int success_count = 0;
                for (int i = 0; i < files.length; i++) {
                    //boolean result = copyFile(files[i].getPath(), target.getPath());
                    boolean result = copyFilesRecursively(files[i].getPath(), target.getPath(), true);
                    success_count += result ? 1 : 0;
                    if (result) {
                        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.parse(files[i].getPath())));
                        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.parse(target.getPath())));
                    }
                }

                Toast.makeText(context, context.getString(R.string.successfully_copied)
                        + String.valueOf(success_count) + "/"
                        + String.valueOf(files.length), Toast.LENGTH_SHORT).show();

            } else if (FileAction.action == FileAction.DELETE) {

                int success_count = 0;
                for (int i = 0; i < files.length; i++) {
                    boolean result = deleteFile(files[i].getPath());
                    success_count += result ? 1 : 0;
                    if (result) {
                        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.parse(files[i].getPath())));
                    }
                }

                Toast.makeText(context, context.getString(R.string.successfully_deleted)
                        + String.valueOf(success_count) + "/"
                        + String.valueOf(files.length), Toast.LENGTH_SHORT).show();
            }
            FileAction.action = FileAction.EMPTY;

            if (callback != null) {
                callback.done();
            }
        }

        private static boolean moveFile(String path, String destination) {
            *//*boolean result = copyFile(path, destination);

            //delete original file
            result = result && deleteFile(path);*//*

            File file = new File(path);
            return file.renameTo(new File(destination, file.getName()));
        }

        private static boolean copyFilesRecursively(String path, String destination, boolean result) {
            File file = new File(path);
            String destinationFileName
                    = getCopyFileName(new File(destination, new File(path).getName()).getPath());
            try {
                result = result && copyFile(path, destinationFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (int i = 0; i < files.length; i++) {
                    copyFilesRecursively(files[i].getPath(),
                            destination + "/" + new File(destinationFileName).getName() + "/", result);
                }
            }
            return result;
        }

        private static boolean copyFile(String path, String destination) throws IOException {
            //create output directory if it doesn't exist
            File dir = new File(destination);
            boolean result;
            if (new File(path).isDirectory()) {
                result = dir.mkdirs();
            } else {
                result = dir.createNewFile();
            }

            InputStream inputStream = new FileInputStream(path);
            OutputStream outputStream = new FileOutputStream(dir);

            byte[] buffer = new byte[1024];
            int bytesRead;
            //copy the file content in bytes
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
            // write the output file
            outputStream.flush();
            outputStream.close();

            inputStream.close();

            Log.d("FileAction", dir.getPath() + " isDir?: " + dir.isDirectory());

            return true;
        }

        private static String getCopyFileName(String destinationPath) {
            File dir = new File(destinationPath);
            String copyName;
            if (dir.exists()) {
                copyName = dir.getPath();
                if (copyName.contains(".")) {
                    int index = copyName.lastIndexOf(".");
                    copyName = copyName.substring(0, index) + " Copy"
                            + copyName.substring(index, copyName.length());
                } else {
                    copyName = copyName + " Copy";
                }
            } else {
                copyName = dir.getPath();
            }
            return copyName;
        }

        private static boolean deleteFile(String path) {
            File file = new File(path);
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (int i = 0; i < files.length; i++) {
                    deleteFile(files[i].getPath());
                }
            }
            return file.exists() && file.delete();
        }

        static String getModeString(Context context) {
            switch (action) {
                case EMPTY:
                    return "empty";
                case MOVE:
                    return context.getString(R.string.move);
                case COPY:
                    return context.getString(R.string.copy);
                case DELETE:
                    return context.getString(R.string.delete);
            }
            return "";
        }
    }*/
}
