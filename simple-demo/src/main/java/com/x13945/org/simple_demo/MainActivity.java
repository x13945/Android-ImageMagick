package com.x13945.org.simple_demo;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tbruyelle.rxpermissions.RxPermissions;

import magick.ImageInfo;
import magick.MagickException;
import magick.MagickImage;
import magick.util.MagickBitmap;
import rx.functions.Action1;

public class MainActivity extends AppCompatActivity {
    private static final String LOGTAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_PICK = 1;
    // Reduce loaded/displayed bitmap to max size:
    private static final int MAX_BITMAP_DIMENSION = 720; // small for testing

    private MainActivity m_This;
    private Prefs m_Prefs;

    private String m_ImagePath = null;
    private MagickImage m_MagickImage = null;
    private MagickImage m_EffectImage = null;

    private TextView m_TextEffect;
    private ImageView m_ImageView;

    private ExportDialog m_ExportDialog = null;
    private View mRotateBtn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_magick);

        m_This = this;
        m_Prefs = new Prefs(this);

        Log.d(LOGTAG, "onCreate()");

        // set image cache temp directory in ImageMagick:
        AndroidMagick.setCacheDir(this);

        m_TextEffect = (TextView) findViewById(R.id.textEffect);
        m_ImageView = (ImageView) findViewById(R.id.imageView);

        mRotateBtn = m_This.findViewById(R.id.rotate_btn);
        mRotateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_MagickImage != null) {
                    showStatus("Working...");
                    applyEffectAsync();
                }
            }
        });

        // restore current image if needed:
        if (savedInstanceState != null)
            m_ImagePath = m_Prefs.getImagePath();

        showStatus("");
        enableUI(true);
    }

    @Override
    public void onStart() {
        super.onStart();

		/*
		if(m_ImagePath != null){
			showStatus("Loading image...");
			loadImage();
		}
		*/
    }

    @Override
    public void onStop() {
        // store current imge path:
        if (m_ImagePath != null)
            m_Prefs.setImagePath(m_ImagePath);

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present:
        getMenuInflater().inflate(R.menu.teste_ndk, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // if UI active:
        if (mRotateBtn.isEnabled()) {
            // Handle action bar item clicks:
            int id = item.getItemId();

            if (id == R.id.load) {
                RxPermissions rxPermissions = RxPermissions.getInstance(this);
                if (!rxPermissions.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            .subscribe(new Action1<Boolean>() {
                                @Override
                                public void call(Boolean aBoolean) {
                                    if (aBoolean) {
                                        pickImage();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Can not read file from device.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                } else {
                    pickImage();
                }
                return true;
            } else if (id == R.id.save) {
                if (m_EffectImage != null) {
                    m_ExportDialog = new ExportDialog(this);
                    m_ExportDialog.show(m_EffectImage);
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(LOGTAG, "onActivityResult()");

        switch (requestCode) {
            case REQUEST_CODE_PICK:
                if (resultCode == RESULT_OK) {
                    Uri targetUri = intent.getData();
                    m_ImagePath = getRealPathFromUri(targetUri);
                    showStatus("Loading image...");
                    loadImage();
                } else {
                    showStatus("");
                    enableUI(true);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }

    private void pickImage() {
        Intent loadPicture = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(loadPicture, REQUEST_CODE_PICK);
    }


    private Bitmap m_Bitmap = null;

    // Thread version:
    private void loadImage() {
        Log.d(LOGTAG, "loadImage()");
        enableUI(false);

        m_Bitmap = null;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                if (m_ImagePath != null) {
                    try {
                        m_MagickImage = new MagickImage(new ImageInfo(m_ImagePath));
                    } catch (MagickException e) {
                        Log.w(LOGTAG, "MagickException - new MagickImage", e);
                        m_MagickImage = null;
                        m_Bitmap = null;
                    }
                    if (m_MagickImage != null) {
                        try {
                            // reduce bitmap size if needed:
                            m_Bitmap = MagickBitmap.ToReducedBitmap(m_MagickImage, MAX_BITMAP_DIMENSION);
                            // m_Bitmap = MagickBitmap.ToBitmap(m_MagickImage);
                        } catch (MagickException e) { // will never happen
                            Log.w(LOGTAG, "MagickException - ToBitmap", e);
                            m_Bitmap = null; // but image is loaded
                        }
                        if (m_Bitmap == null)
                            Log.d(LOGTAG, "ToBitmap null");
                        else
                            Log.d(LOGTAG, "ToBitmap ok");
                    }
                }

                // set as effect image, too:
                m_EffectImage = m_MagickImage;

                m_This.runOnUiThread(new Runnable() {
                    public void run() {
                        enableUI(true);

                        if (m_Bitmap != null) {
                            // show image:
                            m_ImageView.setImageBitmap(m_Bitmap);
                            showStatus("Done");
                            Log.d(LOGTAG, "Load success");
                        } else {
                            if (m_MagickImage == null) {
                                m_ImageView.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_dialog_alert));
                                showStatus("Load failed");
                                Log.d(LOGTAG, "Load failed");
                            } else {
                                m_ImageView.setImageDrawable(getResources().getDrawable(android.R.drawable.gallery_thumb));
                                showStatus("Can't display loaded image");
                                Log.d(LOGTAG, "Can't display loaded image");
                            }
                        }
                    }
                });
            }
        });

        thread.start();
    }
    private void applyEffectAsync() {
        Log.d(LOGTAG, "applyEffectAsync()");
        enableUI(false);

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                if (m_MagickImage != null)
                    return applyEffect();
                else
                    return null;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                enableUI(true);

                if (bitmap != null) {
                    // show modified image:
                    m_ImageView.setImageBitmap(bitmap);
                    showStatus("Done");
                    Log.d(LOGTAG, "Effect success");
                } else {
                    showStatus("Effect failed");
                    Log.d(LOGTAG, "Effect failed");
                }
            }
        }.execute();
    }

    private Bitmap applyEffect() {
        Bitmap bitmap = null;
        m_EffectImage = null;

        Log.d(LOGTAG, "applyEffect()");

        try {
            m_EffectImage = m_MagickImage.rotateImage(90);
            // reduce image if needed:
            bitmap = MagickBitmap.ToReducedBitmap(m_EffectImage, MAX_BITMAP_DIMENSION);
            // bitmap = MagickBitmap.ToBitmap(m_EffectImage);

        } catch (MagickException e) {
            Log.w(LOGTAG, "applyEffect()", e);
            bitmap = null;
        }

        return bitmap;
    }

    private String getRealPathFromUri(Uri contentUri) {
        String path = null;
        String scheme = contentUri.getScheme();

        if (scheme.equals("content")) {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = MediaStore.Images.Media.query(getContentResolver(), contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

            if (cursor != null) {
                if (cursor.moveToFirst())
                    path = cursor.getString(column_index);
                cursor.close();
            }

            cursor = null;
        } else if (scheme.equals("file"))
            path = contentUri.getPath();

        return path;
    }

    private void showStatus(String status) {
        m_TextEffect.setText(status);
    }

    private void enableUI(boolean enable) {
        mRotateBtn.setEnabled(enable);
    }
}
