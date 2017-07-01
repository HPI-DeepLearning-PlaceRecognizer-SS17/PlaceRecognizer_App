package com.placerecognizerstudents.placerecognizer;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.R.attr.bitmap;
import static android.R.attr.data;
import static android.support.v4.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity  {

    private static final int REQUEST_TAKE_PHOTO = 0;
    private static final int REQUEST_PICK_PHOTO = 1;

    private boolean pictureTaken;
    private String picturePath;
    private ImageView pictureImageView;
    private Bitmap bitmap;
    private Bitmap processedBitmap;
    private TextView resultTextView;
    private Button recognizeButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pictureImageView = (ImageView) findViewById(R.id.picture_imageview);
        resultTextView = (TextView)findViewById(R.id.result_text);

        recognizeButton = (Button) findViewById(R.id.recognize_button);

        sharedPreferences = getSharedPreferences("Picture Pref", Context.MODE_PRIVATE);

        recognizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("App", "button clicked");
                if (view != recognizeButton) {
                    Log.d("App", "not button");
                    return;
                }
                if (processedBitmap == null) {
                    Log.d("App", "no bitmap");
                    return;
                }
                new AsyncTask<Bitmap, Void, String>(){
                    @Override
                    protected void onPreExecute() {
                        resultTextView.setText("Calculating...");
                    }

                    @Override
                    protected String doInBackground(Bitmap... bitmaps) {
                        synchronized (recognizeButton) {
                            String tag = MxNetUtils.identifyImage(bitmaps[0]);

                            Log.d("App", "doInBackground");
                            return tag;
                        }
                    }
                    @Override
                    protected void onPostExecute(String tag) {
                        resultTextView.setText(tag);
                    }
                }.execute(processedBitmap);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_place_recognizer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.start_camera_button:
                takePicture();
                Log.d("App", "camera clicked");
                break;
            case R.id.open_gallery_button:
                pickPicture();
                Log.d("App", "gallery clicked");
                break;
            default:
                break;
        }
        return true;
    }

    private void takePicture() {
        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (pictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File newFile = null;
            try {
                newFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (newFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.placerecognizerstudents.placerecognizer.fileprovider", newFile);
                pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(pictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private void pickPicture(){
        final Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            bitmap = BitmapFactory.decodeFile(picturePath);
            processedBitmap = processBitmap(bitmap);
            pictureImageView.setImageBitmap(bitmap);
        }
        if (requestCode == REQUEST_PICK_PHOTO && resultCode == RESULT_OK) {
            try {
                final Uri imageUri = data.getData();
                final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                bitmap = BitmapFactory.decodeStream(imageStream);
                processedBitmap = processBitmap(bitmap);
                pictureImageView.setImageBitmap(processedBitmap);
                Log.d("App", "Image chosen");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        //    Uri imageUri = data.getData();
            Log.d("App", "Image chosen");

         //   pictureImageView.setImageURI(imageUri);

        }
    }

        private File createImageFile () throws IOException {
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );

            // Save a file: path for use with ACTION_VIEW intents
            picturePath = image.getAbsolutePath();
            return image;
        }

    static final int SHORTER_SIDE = 256;
    static final int DESIRED_SIDE = 224;

    private static Bitmap processBitmap(final Bitmap origin) {
        //TODO: error handling
        final int originWidth = origin.getWidth();
        final int originHeight = origin.getHeight();
        int height = SHORTER_SIDE;
        int width = SHORTER_SIDE;
        if (originWidth < originHeight) {
            height = (int)((float)originHeight / originWidth * width);
        } else {
            width = (int)((float)originWidth / originHeight * height);
        }
        final Bitmap scaled = Bitmap.createScaledBitmap(origin, width, height, false);
        int y = (height - DESIRED_SIDE) / 2;
        int x = (width - DESIRED_SIDE) / 2;
        return Bitmap.createBitmap(scaled, x, y, DESIRED_SIDE, DESIRED_SIDE);
    }
}



