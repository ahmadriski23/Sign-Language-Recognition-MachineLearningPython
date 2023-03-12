package com.example.imagepro;


import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

// Change name of this class
public class numberLanguageClass {
    // should start from small letter

    // this is used to load model and predict
    private Interpreter interpreter;
    // create another interpreter for sign_language_model
    private Interpreter interpreter2;
    // This will load sign_language_model
    // store all label in array
    private List<String> labelList;
    private int INPUT_SIZE;
    private int PIXEL_SIZE=3; // for RGB
    private int IMAGE_MEAN=0;
    private  float IMAGE_STD=255.0f;
    // use to initialize gpu in app
    private GpuDelegate gpuDelegate;
    private int height=0;
    private  int width=0;
    private int Classification_Input_Size=0;

    private String final_text="";
    private String current_text="";
    // Now define TextToSpeech
    private TextToSpeech textToSpeech;

    numberLanguageClass(Context context,Button clear_button, Button add_button, TextView change_text,Button text_speech_button, AssetManager assetManager, String modelPath, String labelPath, int inputSize, String classification_model, int classification_input_size) throws IOException{
        INPUT_SIZE=inputSize;
        Classification_Input_Size=classification_input_size;
        // use to define gpu or cpu // no. of threads
        Interpreter.Options options=new Interpreter.Options();
        gpuDelegate=new GpuDelegate();
        options.addDelegate(gpuDelegate);
        options.setNumThreads(4); // set it according to your phone
        // loading model
        interpreter=new Interpreter(loadModelFile(assetManager,modelPath),options);
        // load labelmap
        labelList=loadLabelList(assetManager,labelPath);

        // Code for loading model
        Interpreter.Options options2=new Interpreter.Options();
        // add 2 thread to this interpreter
        options2.setNumThreads(2);
        // load model
        interpreter2=new Interpreter(loadModelFile(assetManager,classification_model),options2);

        clear_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final_text="";
                change_text.setText(final_text);
            }
        });

        add_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final_text=final_text+current_text;
                change_text.setText(final_text);
            }
        });

        // Initialize TextToSpeech
        textToSpeech=new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // If there is no error
                if (status !=TextToSpeech.ERROR){
                    // set textToSpeech language
                    textToSpeech.setLanguage(Locale.ENGLISH);
                    // I will use English Language, you can use any language you want


                }
            }
        });
        // Now use setOnClickListener on text_speech_button
        text_speech_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // When this button is clicked Read the text
                // The text that i want to read is final_text
                textToSpeech.speak(final_text,TextToSpeech.QUEUE_FLUSH,null);
                // This is all you have to do to add text to speech converter
            }
        });





    }

    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        // to store label
        List<String> labelList=new ArrayList<>();
        // create a new reader
        BufferedReader reader=new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        // loop through each line and store it to labelList
        while ((line=reader.readLine())!=null){
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        // use to get description of file
        AssetFileDescriptor fileDescriptor=assetManager.openFd(modelPath);
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset =fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }
    // create new Mat function
    public Mat recognizeImage(Mat mat_image){
        // Rotate original image by 90 degree get get portrait frame

        // This change was done in video: Does Your App Keep Crashing? | Watch This Video For Solution.
        // This will fix crashing problem of the app

        Mat rotated_mat_image=new Mat();

        Mat a=mat_image.t();
        Core.flip(a,rotated_mat_image,1);
        // Release mat
        a.release();

        // if you do not do this process you will get improper prediction, less no. of object
        // now convert it to bitmap
        Bitmap bitmap=null;
        bitmap=Bitmap.createBitmap(rotated_mat_image.cols(),rotated_mat_image.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rotated_mat_image,bitmap);
        // define height and width
        height=bitmap.getHeight();
        width=bitmap.getWidth();

        // scale the bitmap to input size of model
        Bitmap scaledBitmap=Bitmap.createScaledBitmap(bitmap,INPUT_SIZE,INPUT_SIZE,false);

        // convert bitmap to bytebuffer as model input should be in it
        ByteBuffer byteBuffer=convertBitmapToByteBuffer(scaledBitmap);

        // defining output
        // 10: top 10 object detected
        // 4: there coordinate in image
        //  float[][][]result=new float[1][10][4];
        Object[] input=new Object[1];
        input[0]=byteBuffer;

        Map<Integer,Object> output_map=new TreeMap<>();
        // we are not going to use this method of output
        // instead we create treemap of three array (boxes,score,classes)

        float[][][]boxes =new float[1][10][4];
        // 10: top 10 object detected
        // 4: there coordinate in image
        float[][] scores=new float[1][10];
        // stores scores of 10 object
        float[][] classes=new float[1][10];
        // stores class of object

        // add it to object_map;
        output_map.put(0,boxes);
        output_map.put(1,classes);
        output_map.put(2,scores);

        // now predict
        interpreter.runForMultipleInputsOutputs(input,output_map);
        // Before watching this video please watch my previous 2 video of
        //      1. Loading tensorflow lite model
        //      2. Predicting object
        // In this video we will draw boxes and label it with it's name

        Object value=output_map.get(0);
        Object Object_class=output_map.get(1);
        Object score=output_map.get(2);

        // loop through each object
        // as output has only 10 boxes
        for (int i=0;i<10;i++){
            // here we are looping through each hand which is detected
            float class_value=(float) Array.get(Array.get(Object_class,0),i);
            float score_value=(float) Array.get(Array.get(score,0),i);
            // define threshold for score

            // Here you can change threshold according to your model
            // Now we will do some change to improve app
            if(score_value>0.5){
                Object box1=Array.get(Array.get(value,0),i);
                // we are multiplying it with Original height and width of frame
                // Change this into x1,y1 and x2,y2 co-ordinates
                float y1=(float) Array.get(box1,0)*height;
                float x1=(float) Array.get(box1,1)*width;
                float y2=(float) Array.get(box1,2)*height;
                float x2=(float) Array.get(box1,3)*width;

                // set boundary limit
                if(y1<0){
                    y1=0;
                }
                if(x1<0){
                    x1=0;
                }
                if(x2>width){
                    x2=width;
                }
                if (y2>height){
                    y2=height;
                }
                // now set height and width of box
                float w1=x2-x1;
                float h1=y2-y1;
                // So if you don't know
                // (x1,y1) is that starting of hand
                // (x2,y2) is the end point of hand

                // crop hand image from original frame
                Rect cropped_roi=new Rect((int)x1,(int)y1,(int)w1,(int)h1);
                Mat cropped=new Mat(rotated_mat_image,cropped_roi).clone();
                // Now convert this cropped Mat to Bitmap
                Bitmap bitmap1=null;
                bitmap1=Bitmap.createBitmap(cropped.cols(),cropped.rows(),Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(cropped,bitmap1);

                // Resize bitmap1 to classification input size=96
                Bitmap scaledBitmap1=Bitmap.createScaledBitmap(bitmap1,Classification_Input_Size,Classification_Input_Size,false);
                // Convert scaledBitmap1 to byte buffer
                ByteBuffer byteBuffer1=convertBitmapToByteBuffer1(scaledBitmap1);
                // create  an array for output of interpreter2
                float[][] output_class_value=new float[1][1];

                // Predict output for byteBuffer1
                interpreter2.run(byteBuffer1,output_class_value);

                // If you want to output_class_value
                Log.d("numberLanguageClass", "output_class_value: "+output_class_value[0][0]);

                // convert output_class_value to alphabets
                // now create alphabets function
                String sign_val=get_alphabets(output_class_value[0][0]);
                current_text=sign_val;


                // Use puttext to add Class name in image
                //              input/output        text               starting point           font size                       text color(white)
                Imgproc.putText(rotated_mat_image,""+sign_val,new Point(x1+10,y1+40),2,1.5,new Scalar(255, 255, 255, 255),2);
                Imgproc.rectangle(rotated_mat_image,new Point(x1,y1),new Point(x2,y2),new Scalar(0, 255, 0, 255),2);// color of text           // size of text





            }


        }

        Mat b=rotated_mat_image.t();
        Core.flip(b,mat_image,0);
        b.release();
        // Now for second change go to CameraBridgeViewBase
        return mat_image;
    }

    private String get_alphabets(float sign_v) {
        String val="";
        if(sign_v>=0.5 & sign_v<0.5){
            val="1";
        }
        else if(sign_v >=0.5 & sign_v <1.5){
            val="2";
        }

        else if(sign_v >=1.5 & sign_v <2.5){
            val="3";
        }
        else if(sign_v >=2.5 & sign_v <3.5){
            val="4";
        }
        else if(sign_v >=3.5 & sign_v <4.5){
            val="5";
        }
        else if(sign_v >=4.5 & sign_v <5.5){
            val="6";
        }
        else if(sign_v >=5.5 & sign_v <6.5){
            val="7";
        }
        else if(sign_v >=6.5 & sign_v <7.5){
            val="8";
        }
        else if(sign_v >=7.5 & sign_v <8.5){
            val="9";
        }
        else {
            val="10";
        }

        return val;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        // some model input should be quant=0  for some quant=1
        // for this quant=0
        // Change quant=1
        // As we are scaling image from 0-255 to 0-1
        int quant=1;
        int size_images=INPUT_SIZE;
        if(quant==0){
            byteBuffer=ByteBuffer.allocateDirect(1*size_images*size_images*3);
        }
        else {
            byteBuffer=ByteBuffer.allocateDirect(4*1*size_images*size_images*3);
        }
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_images*size_images];
        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;

        // some error
        //now run
        for (int i=0;i<size_images;++i){
            for (int j=0;j<size_images;++j){
                final  int val=intValues[pixel++];
                if(quant==0){
                    byteBuffer.put((byte) ((val>>16)&0xFF));
                    byteBuffer.put((byte) ((val>>8)&0xFF));
                    byteBuffer.put((byte) (val&0xFF));
                }
                else {
                    // paste this
                    byteBuffer.putFloat((((val >> 16) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val >> 8) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val) & 0xFF))/255.0f);
                }
            }
        }
        return byteBuffer;
    }

    // create a copy of convertBitmapToByteBuffer
    // add 1

    private ByteBuffer convertBitmapToByteBuffer1(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        int quant=1;
        // Change input size
        int size_images=Classification_Input_Size;
        if(quant==0){
            byteBuffer=ByteBuffer.allocateDirect(1*size_images*size_images*3);
        }
        else {
            byteBuffer=ByteBuffer.allocateDirect(4*1*size_images*size_images*3);
        }
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_images*size_images];
        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;
        // remove 255.0f as we did not scale the image
        for (int i=0;i<size_images;++i){
            for (int j=0;j<size_images;++j){
                final  int val=intValues[pixel++];
                if(quant==0){
                    byteBuffer.put((byte) ((val>>16)&0xFF));
                    byteBuffer.put((byte) ((val>>8)&0xFF));
                    byteBuffer.put((byte) (val&0xFF));
                }
                else {

                    byteBuffer.putFloat((((val >> 16) & 0xFF)));
                    byteBuffer.putFloat((((val >> 8) & 0xFF)));
                    byteBuffer.putFloat((((val) & 0xFF)));
                }
            }
        }
        return byteBuffer;
    }
}