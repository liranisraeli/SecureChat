package com.example.securechat;
//import
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;



public class MainActivity extends AppCompatActivity {


    public static final int RC_SIGN_IN=1;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosReference;
    private static final int RC_PHOTO_PICKER =  2;
    public  static String pwdtext="qwerty";
    private String mUsername;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFirebaseDatabase =FirebaseDatabase.getInstance();
        mFirebaseAuth=FirebaseAuth.getInstance();
        mMessagesDatabaseReference=mFirebaseDatabase.getReference().child("messages");
        mUsername ="liran";
        mFirebaseStorage=FirebaseStorage.getInstance();
        mChatPhotosReference=mFirebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar =  findViewById(R.id.progressBar);
        mMessageListView =  findViewById(R.id.messageListView);
        mPhotoPickerButton =  findViewById(R.id.photoPickerButton);
        mMessageEditText =  findViewById(R.id.messageEditText);
        mSendButton =  findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String temp=mMessageEditText.getText().toString();
                String encrypted="";
                try {
                    encrypted=encrypt(temp,pwdtext);
                } catch (Exception e) {
                    e.printStackTrace();
                }


                FriendlyMessage friendlyMessage = new FriendlyMessage( encrypted, mUsername, null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener= firebaseAuth -> {
            FirebaseUser user=firebaseAuth.getCurrentUser();
            if(user!=null) // user is logged in
            {
                onSignedInInitialize(user.getDisplayName());
            }
            else
            {
                onSignedOutCleanUp();
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setIsSmartLockEnabled(false)
                                .setAvailableProviders(Arrays.asList(
                                        new AuthUI.IdpConfig.GoogleBuilder().build(),
                                        new AuthUI.IdpConfig.EmailBuilder().build()
                                ))
                                .build(),
                        RC_SIGN_IN);
            }
        };
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
        else if(resultCode==RESULT_OK&&requestCode==RC_PHOTO_PICKER)
        {
            Toast.makeText(MainActivity.this,"seccuessfull",Toast.LENGTH_SHORT).show();
            Uri selectedImageUri=data.getData();
            final StorageReference photoRef=
                    mChatPhotosReference.child(selectedImageUri.getLastPathSegment());
            UploadTask uploadTask=photoRef.putFile(selectedImageUri);
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this,"Unsuccuessfull",Toast.LENGTH_SHORT).show();
                }
            });

            Task<Uri> uriTask=uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if(!task.isSuccessful())
                        throw  task.getException();
                    return photoRef.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if(task.isSuccessful())
                    {
                        Uri downloadUri=task.getResult();
                        FriendlyMessage friendlyMessage=new FriendlyMessage(null,mUsername,downloadUri.toString());
                        mMessagesDatabaseReference.push().setValue(friendlyMessage);
                    }
                    else
                    {
                        Toast.makeText(MainActivity.this,"Unseccuessfull",Toast.LENGTH_SHORT).show();
                    }
                }
            });

        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAuthStateListener!=null){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detactDatabaseReadListner();
        mMessageAdapter.clear();

    }
    private  void onSignedInInitialize(String username)
    {
        mUsername=username;
        attactDatabaseReadListner();
    }
    private void onSignedOutCleanUp()
    {
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
        detactDatabaseReadListner();
    }
    private void attactDatabaseReadListner()
    {

        if(mChildEventListener==null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
                    String to_decrypt=friendlyMessage.getText();
                    String decrypted="";
                    try {
                        decrypted=decrypt(to_decrypt,pwdtext);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    friendlyMessage.setText(decrypted);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };

            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }
    private void detactDatabaseReadListner()
    {
        if(mChildEventListener!=null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener=null;
        }
    }


    private String encrypt(String data, String password_text) throws Exception {
        SecretKeySpec key = generateKey(password_text);
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");//creating an object
        c.init(Cipher.ENCRYPT_MODE, key);//initialisation
        byte[] encVal = c.doFinal(data.getBytes("UTF-8"));
        //Encrypts or decrypts data in a single-part operation, or finishes a multiple-part operation
        String encryptedvalue = Base64.encodeToString(encVal, Base64.DEFAULT);
        //Base64-encode the given data and return new allocated String with the result.
        //It's basically a way of encoding arbitrary binary data in ASCII text. It takes 4 characters per 3 bytes of data,
        // plus potentially a bit of padding at the end.
        //Essentially each 6 bits of the input is encoded in a 64-character alphabet.
        //The "standard" alphabet uses A-Z, a-z, 0-9 and + and /, with = as a padding character. There are URL-safe variants.
        return encryptedvalue;

    }

    private SecretKeySpec generateKey(String password) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");//for using hash function SHA-256
        byte[] bytes = password.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return secretKeySpec;
    }
    private String decrypt(String data, String password_text) throws Exception {
        SecretKeySpec key = generateKey(password_text);
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedvalue = Base64.decode(data, Base64.DEFAULT);
        byte[] decvalue = c.doFinal(decodedvalue);//final decoding operation
        String decryptedvalue = new String(decvalue, "UTF-8");//converting bytes into string
        return decryptedvalue;
    }

}
