package mp3tagedit.de.main;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Used to handle everything that is needed for selecting the album cover picture
 * or generating it
 *
 * @author Christian
 */
public class AlbumCoverActivity extends AppCompatActivity {

    public static final int PERMISSIONS_REQUEST_CAMERA = 44;
    private static final int RESULT_CAMERA = 1;
    private static final int RESULT_GALLERY = 2;
    private static final int RESULT_CROP = 3;
    private static final int RESULT_GEN = 4;

    private File PhotoFile;
    private Uri photoURI;

    private CoverGenTags tags;

    /**
     * Listeners to handle clicks on the BottomNavigationView
     */
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(AlbumCoverActivity.this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                return false;
            }
            switch (item.getItemId()) {
                case R.id.navigation_camera:
                    takePhoto();
                    break;
                case R.id.navigation_generate:
                    startGeneration();
                    break;
                case R.id.navigation_gallery:
                    openGallery();
                    break;
            }
            return false;
        }
    };

    /**
     * starts the Activity for generating a cover and passes and instance of CoverGenTags
     */
    private void startGeneration() {
        Intent i = new Intent(this, CoverGenerationActivity.class);
        i.putExtra("tags", tags);
        startActivityForResult(i, RESULT_GEN);
    }

    /**
     * Retrieves all necessary data from the Intent and sets up the view
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_cover);

        File tmp = new File(getExternalFilesDir("").getAbsolutePath(),  "Pic.jpeg");
        // if the temporary file already exists it gets cleared to be later rewritten
        if (tmp.exists()) {
            tmp.delete();
        }

        PhotoFile = new File(getExternalFilesDir("").getAbsolutePath(),  "Pic.jpeg");
        photoURI = FileProvider.getUriForFile(getApplicationContext(), "thejetstream.de.fileprovider", PhotoFile);

        ImageView CoverView = findViewById(R.id.CoverView);
        tags = (CoverGenTags) getIntent().getSerializableExtra("tags");

        // loads the image fro mthe Intent if it was transmitted
        if(tags != null && tags.getImage() != null){
            CoverView.setImageBitmap(tags.getImage());

            if (photoURI == null) {
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(PhotoFile);
                tags.getImage().compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.close();
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
            }
        }

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.getMenu().getItem(0).setCheckable(false);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        Button save = findViewById(R.id.button_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent();
                i.putExtra("tags", tags);

                byte[] byteToRet = new byte[]{0};

                try {
                    Bitmap toRet = BitmapFactory.decodeFile(getExternalFilesDir(null) + "/Pic.jpeg");
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    toRet.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                    byteToRet = stream.toByteArray();
                } catch (Exception e) { /* ignore */ }


                i.putExtra("path", byteToRet);
                setResult(RESULT_OK, i);
                finish();
            }
        });
    }

    /**
     * Overrides existing method, waits for the result of any activity launched in this activity
     * and handles what is in the Intents <br>
     * In this class it handles the different images transmitted if they exist
     * @param requestCode  The code of the terminating Activity
     * @param data The data transmitted by the terminating Activity
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_CAMERA:
                if (resultCode == Activity.RESULT_OK) {
                    Uri selectedImage = photoURI;
                    getContentResolver().notifyChange(selectedImage, null);
                    cropImage();
                }
                break;
            case RESULT_GALLERY:
                if (resultCode == Activity.RESULT_OK && null != data) {
                    Uri selectedImage = data.getData();
                    saveFile(selectedImage, PhotoFile);
                    cropImage();
                }
                break;
            case RESULT_CROP:
                if (resultCode == Activity.RESULT_OK) {
                    Uri selectedImage = photoURI;
                    getContentResolver().notifyChange(selectedImage, null);
                    ImageView CoverView = findViewById(R.id.CoverView);
                    ContentResolver cr = getContentResolver();
                    Bitmap bitmap;
                    try {
                        bitmap = MediaStore.Images.Media
                                .getBitmap(cr, selectedImage);

                        CoverView.setImageBitmap(bitmap);
                        Toast.makeText(this, selectedImage.toString(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT)
                                .show();
                        Log.e("Camera", e.toString());
                    }
                }
                break;
            case RESULT_GEN:
                if (resultCode == Activity.RESULT_OK) {
                    Bitmap bitmap = null;
                    ImageView CoverView = findViewById(R.id.CoverView);
                    try {
                        bitmap = ((CoverGenTags) data.getSerializableExtra("active")).getImage();

                        CoverView.setImageBitmap(bitmap);
                        Toast.makeText(this, "Loaded", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT)
                                .show();
                        Log.e("Camera", e.toString());
                    }

                    String TAG = "GEN";

                    if (photoURI == null) {
                        Log.d(TAG, "Error creating media file, check storage permissions: ");// e.getMessage());
                        return;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(PhotoFile);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                        fos.close();
                    } catch (FileNotFoundException e) {
                        Log.d(TAG, "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "Error accessing file: " + e.getMessage());
                    }
                    tags = (CoverGenTags) data.getSerializableExtra("active");
                }
                break;
        }
    }

    /**
     * Opens the Camera
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        startActivityForResult(intent, RESULT_CAMERA);
    }

    /**
     * Opens the Gallery
     */
    public void openGallery(){
        Intent galleryIntent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent , RESULT_GALLERY );
    }

    /**
     * Initializes the cropping of the current Image
     */
    public void cropImage(){
        getApplicationContext().grantUriPermission("com.android.camera",photoURI,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(photoURI, "image/*");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 500);
        intent.putExtra("outputY", 500);
        intent.putExtra("return-data", true);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        startActivityForResult(intent, RESULT_CROP);
    }

    /**
     * Takes a Uri and saves the file
     * @param sourceUri the Uri to be read from
     * @param destination the file that should be rewritten
     */
    private void saveFile(Uri sourceUri, File destination) {
        try {
            FileChannel src = ((FileInputStream) getContentResolver().openInputStream(sourceUri)).getChannel();
            FileChannel dst = new FileOutputStream(destination).getChannel();
            dst.transferFrom(src, 0, src.size());
            src.close();
            dst.close();
            Log.e("saving", "SUCCESS?");
        } catch (IOException ex) {
            Log.e("saving", "FAILURE");
            ex.printStackTrace();
        }
    }
}
