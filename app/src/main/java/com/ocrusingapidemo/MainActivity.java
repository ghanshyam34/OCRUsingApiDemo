package com.ocrusingapidemo;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Created by Ghanshyam on 6/25/2016.
 */
public class MainActivity extends Activity {

    private static final int SELECT_PICTURE = 1;
    private static final int TAKE_PICTURE = 2;
    private String mImageFullPathAndName = "";
    private String localImagePath = "";
    private static final int OPTIMIZED_LENGTH = 1024;

    private  final  String idol_ocr_service = "https://api.idolondemand.com/1/api/async/ocrdocument/v1?";
    private  final  String idol_ocr_job_result = "https://api.idolondemand.com/1/job/result/";
    private String jobID = "";

    CommsEngine commsEngine;

    ImageView ivSelectedImg;
    EditText edTextResult;
    LinearLayout llResultContainer;
    LinearLayout llCameraButtons;
    LinearLayout llOperations;
    ProgressBar pbOCRReconizing;

    byte[] myFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivSelectedImg = (ImageView) findViewById(R.id.imageView);
        llCameraButtons = (LinearLayout) findViewById(R.id.llcamerabuttons);
        llOperations = (LinearLayout) findViewById(R.id.lloptions);

        edTextResult = (EditText) findViewById(R.id.etresult);
        llResultContainer = (LinearLayout) findViewById(R.id.llresultcontainer);
        pbOCRReconizing = (ProgressBar) findViewById(R.id.pbocrrecognizing);

        CreateLocalImageFolder();

        Button bstartcamera = (Button)findViewById(R.id.bstartcamera);
        bstartcamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                checkPermissions();

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (commsEngine == null)
            commsEngine = new CommsEngine();
    }
    @Override
    protected void onPause() {
        super.onPause();

    }
    @Override public void onBackPressed() {
        if (llResultContainer.getVisibility() == View.VISIBLE) {
            llResultContainer.setVisibility(View.GONE);
            ivSelectedImg.setVisibility(View.VISIBLE);
            return;
        } else
            finish();
    }
    public void DoStartOCR(View v) {
        pbOCRReconizing.setVisibility(View.VISIBLE);
        if (jobID.length() > 0)
            getResultByJobId();

        else if (!mImageFullPathAndName.isEmpty()){


            Map<String,String> map =  new HashMap<String,String>();
            map.put("file",mImageFullPathAndName);
            String fileType = "image/*";
            map.put("mode", "document_photo");
            map.put("languages", "en");


            commsEngine.ServicePostRequest(idol_ocr_service, fileType, map, new OnServerRequestCompleteListener() {
                @Override
                public void onServerRequestComplete(String response) {
                    try {
                        JSONObject mainObject = new JSONObject(response);
                        if (!mainObject.isNull("jobID")) {
                            jobID = mainObject.getString("jobID");
                            getResultByJobId();
                        } else
                            ParseSyncResponse(response);
                    } catch (Exception ex) {

                    }
                }
                @Override
                public void onErrorOccurred(String error) {
                    // handle error
                }
            });


        } else
            Toast.makeText(this, "Please select an image.", Toast.LENGTH_LONG).show();
    }


    private void getResultByJobId() {
        String param = idol_ocr_job_result + jobID + "?";
        commsEngine.ServiceGetRequest(param, "", new
                OnServerRequestCompleteListener() {
                    @Override
                    public void onServerRequestComplete(String response) {
                        ParseAsyncResponse(response);
                    }
                    @Override
                    public void onErrorOccurred(String error) {
                        // handle error
                    }
                });
    }

    public void DoCloseResult(View v) {
        ivSelectedImg.setVisibility(View.VISIBLE);
        llResultContainer.setVisibility(View.GONE);
    }
    private void ParseSyncResponse(String response) {
        pbOCRReconizing.setVisibility(View.GONE);
        if (response == null) {
            Toast.makeText(this, "Unknown error occurred. Try again", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONObject mainObject = new JSONObject(response);
            JSONArray textBlockArray = mainObject.getJSONArray("text_block");
            int count = textBlockArray.length();
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    JSONObject texts = textBlockArray.getJSONObject(i);
                    String text = texts.getString("text");
                    ivSelectedImg.setVisibility(View.GONE);
                    llResultContainer.setVisibility(View.VISIBLE);
                    edTextResult.setText(text);
                }
            }
            else
                Toast.makeText(this, "Not available", Toast.LENGTH_LONG).show();
        } catch (Exception ex){}
    }
    private void ParseAsyncResponse(String response) {
        pbOCRReconizing.setVisibility(View.GONE);
        if (response == null) {
            Toast.makeText(this, "Unknown error occurred. Try again", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONObject mainObject = new JSONObject(response);
            JSONArray textBlockArray = mainObject.getJSONArray("actions");
            int count = textBlockArray.length();
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    JSONObject actions = textBlockArray.getJSONObject(i);
                    String action = actions.getString("action");
                    String status = actions.getString("status");
                    JSONObject result = actions.getJSONObject("result");
                    JSONArray textArray = result.getJSONArray("text_block");
                    count = textArray.length();
                    if (count > 0) {
                        for (int n = 0; n < count; n++) {
                            JSONObject texts = textArray.getJSONObject(n);
                            String text = texts.getString("text");
                            ivSelectedImg.setVisibility(View.GONE);
                            llResultContainer.setVisibility(View.VISIBLE);
                            edTextResult.setText(text);
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Not available", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
        }
    }


    public static String _path;
    public static String pdf_path;
    public static String txtfile_path;
    File file;

    public void CreateLocalImageFolder()
    {

        String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
        String APP_FOLDER = extStorageDirectory + "/CustomOcrApp/";
        File wallpaperDirectory = new File(APP_FOLDER + "/");
        wallpaperDirectory.mkdirs();
        _path = wallpaperDirectory + "/temp.jpg";
        pdf_path = wallpaperDirectory +"/ImageAsspossAdded.pdf";
        txtfile_path = wallpaperDirectory +"/Extracted_text.txt";


        if (localImagePath.length() == 0)
        {
            localImagePath = getFilesDir().getAbsolutePath() + "/orc/";
            File folder = new File(localImagePath);
            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdir();
            }
            if (!success)
                Toast.makeText(this, "Cannot create local folder", Toast.LENGTH_LONG).show();
        }
    }

    String[] mypermissions= new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            /*Manifest.permission.CAMERA,*/
            Manifest.permission.READ_EXTERNAL_STORAGE,
           /* Manifest.permission.READ_CONTACTS*/
            Manifest.permission.CAMERA
    };

    String[] mypermissionsDenied = new String[]{
            "Permission Denied to write on external storage",
          /*  "Permission Denied to use camera",*/
            "Permission Denied to read from external storage",
           /* "Permission Denied to read contacts"*/
            "Permission Denied to open camera",
    };

    private  void checkPermissions() {

        if (Build.VERSION.SDK_INT >= 23) {

            int result;
            List<String> listPermissionsNeeded = new ArrayList<>();
            for (String p : mypermissions) {
                result = ContextCompat.checkSelfPermission(this, p);
                if (result != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(p);
                }
            }
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),111);

            }else{

                boolean autoFocus = true;
                boolean useFlash = false;

                DoTakePhoto(null);

            }

        }else{

            boolean autoFocus = true;
            boolean useFlash = false;
            DoTakePhoto(null);

        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean isPermissionGranted = true;

        switch(requestCode) {

            case 111:

                if (grantResults != null && grantResults.length == mypermissions.length) {

                    for (int i = 0; i < grantResults.length; i++) {

                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {

                            Log.i("PERMISSION_DENIED",mypermissions[i]);

                            isPermissionGranted = false;
                            Toast.makeText(MainActivity.this, mypermissionsDenied[i],1).show();

                        }else{

                            Log.i("PERMISSION_GRANTED",mypermissions[i]);

                        }
                    }
                }
                break;
        }

        if(isPermissionGranted){

            DoTakePhoto(null);

        }else{

            Toast.makeText(MainActivity.this,"please install again and set permission manually",Toast.LENGTH_SHORT).show();
        }
    }

    public Bitmap decodeFile(File file) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        int mImageRealWidth = options.outWidth;
        int mImageRealHeight = options.outHeight;
        Bitmap pic = null;
        try {
            pic = BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception ex) {
            Log.e("MainActivity", ex.getMessage());
        }
        return pic;
    }
    public Bitmap rescaleBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }
    private Bitmap rotateBitmap(Bitmap pic, int deg) {
        Matrix rotate90DegAntiClock = new Matrix();
        rotate90DegAntiClock.preRotate(deg);
        Bitmap newPic = Bitmap.createBitmap(pic, 0, 0, pic.getWidth(), pic.getHeight(), rotate90DegAntiClock, true);
        return newPic;
    }

    private String SaveImage(Bitmap image)
    {
        String fileName = localImagePath + "imagetoocr.jpg";
        try {

            File file = new File(fileName);
            FileOutputStream stream = new FileOutputStream(file);

            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);

            try {
                stream.flush();
                stream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return fileName;
    }


    private byte[] getbyteArray(Bitmap image)
    {

        byte[] byteArray = null;
        String fileName = localImagePath + "imagetoocr.jpg";
        try {

            File file = new File(fileName);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byteArray = stream.toByteArray();

            try {
                stream.flush();
                stream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

//        myFile = file;

        return byteArray;
    }


    public byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        byte bytes[] = new byte[size];
        byte tmpBuff[] = new byte[size];
        FileInputStream fis= new FileInputStream(f);;
        try {

            int read = fis.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        }  catch (IOException e){
            throw e;
        } finally {
            fis.close();
        }

        return bytes;
    }


    public void DoTakePhoto(View view) {

        try {

            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
            startActivityForResult(intent, TAKE_PICTURE);

        }catch (Exception e){

            e.printStackTrace();

        }

    }

    public void DoShowSelectImage(View v) {

        Intent i = new Intent(
                Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, SELECT_PICTURE);

//        readTextAbsorb();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_PICTURE || requestCode == TAKE_PICTURE) {

            if (resultCode == RESULT_OK && null != data) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                Cursor cursor = getContentResolver().query(selectedImage,
                        filePathColumn, null, null, null);
                cursor.moveToFirst();

                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                mImageFullPathAndName = cursor.getString(columnIndex);
                cursor.close();
                jobID = "";
                File file = new File(mImageFullPathAndName);

                new SaveImageTask(file).execute();

            }
        }
    }


    public class SaveImageTask extends AsyncTask<Void,Void, Void> {

        File tempFile,mFile;
        public SaveImageTask(File ff){
            mFile = ff;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            hideProgress();

            Bitmap mCurrentSelectedBitmap = decodeFile(tempFile);

            ivSelectedImg.setImageBitmap(mCurrentSelectedBitmap);

            mImageFullPathAndName = tempFile.getAbsolutePath();

            try {
                myFile  = fullyReadFileToBytes(tempFile);//getbyteArray(mCurrentSelectedBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress();
        }
        @Override
        protected Void doInBackground(Void... params) {

            tempFile = rotateImage(mFile);
            return null;

        }
    }


    int rotate;
    public File rotateImage(File uri){
        File file = uri;
        try{
            try {

                ExifInterface exif = new ExifInterface(
                        file.getAbsolutePath());
                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                Log.v("my", "Exif orientation: " + orientation);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        Log.v("", "rotated " +270);
                        rotate = 270;
                        Log.e("rotate", ""+rotate);
                        ImageOrientation(file,rotate);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        Log.v("", "rotated " +180);
                        rotate = 180;
                        Log.e("rotate", ""+rotate);
                        ImageOrientation(file,rotate);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_90:
                        Log.v("", "rotated " +90);
                        rotate = 90;
                        ImageOrientation(file,rotate);
                        break;

                    case 1:
                        Log.v("", "rotated1-" +90);
                        rotate = 90;
                        ImageOrientation(file,rotate);
                        break;

                    case 2:
                        Log.v("", "rotated1-" +0);
                        rotate = 0;
                        ImageOrientation(file,rotate);
                        break;
                    case 4:
                        Log.v("", "rotated1-" +180);
                        rotate = 180;
                        ImageOrientation(file,rotate);
                        break;

                    case 0:
                        Log.v("", "rotated 0-" +90);
                        rotate = 90;
                        ImageOrientation(file,rotate);
                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        catch(Exception e){
            Log.e("Error - ", e.getMessage());
        }
        return file;
    }

    private void ImageOrientation(File file,int rotate){
        try {
            FileInputStream fis = new FileInputStream(file);
            Bitmap photo = BitmapFactory.decodeStream(fis);
            Matrix matrix = new Matrix();
            matrix.preRotate(rotate); // clockwise by 90 degrees
            photo = Bitmap.createBitmap(photo , 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            //write the bytes in file
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(bitmapdata);
                fos.close();
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    ProgressDialog progress;
    public void showProgress() {

        try {

            if (progress == null)
                progress = new ProgressDialog(MainActivity.this);
            progress.setMessage("Please Wait..");
            progress.setCancelable(false);
            progress.show();

        } catch (Exception e) {

            e.printStackTrace();
            try {

                progress = new ProgressDialog(MainActivity.this);
                progress.setMessage("Please Wait..");
                progress.setCancelable(false);
                progress.show();

            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }


    public void hideProgress() {

        if (progress != null && progress.isShowing()) {

            progress.dismiss();

        }
    }
}
