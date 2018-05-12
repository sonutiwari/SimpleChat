/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package in.co.ikai.simplechat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.co.ikai.simplechat.adapters.MessageAdapter;
import in.co.ikai.simplechat.dataModels.FriendlyMessage;

public class MainActivity extends AppCompatActivity {

    // Declaring global objects and static fields.
    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String FRIENDLY_MESSAGE_LENGTH_KEY = "friendly_message_length";

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private FirebaseDatabase mFirebaseDatabase;
    private StorageReference mChatPhotosStorageReference;
    private FirebaseStorage mFirebaseStorage;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private DatabaseReference mDatabaseReference;
    private ChildEventListener mChildEventListener;


    private String mUsername;
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Getting reference of Firebase authentication
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // Getting reference of database.
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);
        mMessageListView = findViewById(R.id.messageListView);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

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
                // TODO: Fire an intent to show an image picker
                // ImagePickerButton shows an image picker to upload a image for a message
                mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/jpeg");
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                        startActivityForResult(Intent.createChooser(intent
                                , "Complete action using"), RC_PHOTO_PICKER);
                    }
                });
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
        mMessageEditText
                .setFilters(
                        new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click

                FriendlyMessage friendlyMessage =
                        new FriendlyMessage(mMessageEditText.getText().toString()
                                , mUsername, null);
                mDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    onSignedInInitialize(user.getDisplayName());
                } else {
                    // User is signed out.
                    onSignedOutCleanUp();
                    //  Take him to login screen
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.PhoneBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.FacebookBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        // Setting remote configuration.
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings
                                                                .Builder()
                                                                .setDeveloperModeEnabled(
                                                                        BuildConfig.DEBUG
                                                                ).build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MESSAGE_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();
    }

    private void fetchConfig() {
        long cacheExpiration = 3600;
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();
                applyRetrievedLength();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                applyRetrievedLength();
            }
        });
    }

    private void applyRetrievedLength() {
        Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MESSAGE_LENGTH_KEY);
        mMessageEditText.setFilters(new  InputFilter[]{
                                            new InputFilter.LengthFilter(
                                                    friendly_msg_length.intValue()
                                            )
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Checking if it is from sign in flow.
        if (requestCode == RC_SIGN_IN){
            if (resultCode == RESULT_CANCELED){
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){
            Uri selectedImageUri = data.getData();
            StorageReference photoRef =
                    mChatPhotosStorageReference
                            .child(selectedImageUri.getLastPathSegment());
            UploadTask uploadTask = photoRef.putFile(selectedImageUri);
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(MainActivity.this
                            , "uploaded", Toast.LENGTH_SHORT).show();
                    Uri downloadUri = taskSnapshot.getDownloadUrl();

                    // Set the download URL to the message box, so that the user
                    // can send it to the database.
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null
                            , mUsername, downloadUri.toString());
                    mDatabaseReference.push().setValue(friendlyMessage);
                }
            });
        }

    }


    // Cleaning data once user signed out.
    private void onSignedOutCleanUp() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();

        // Detach database listener now.
        detachDatabaseReadListener();
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null){
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    // Initializing data so that it is available once user signed in.
    private void onSignedInInitialize(String displayName) {
        mUsername = displayName;

        // Attaching child event listener.
        attachDatabaseReadListener();
    }

    // Attach database read listener.
    private void attachDatabaseReadListener(){

        // Checking if event listener is detached.
        if (mChildEventListener == null) {
            // Reading the data base once authenticated.
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);

                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            mDatabaseReference.addChildEventListener(mChildEventListener);
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
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
                default:
                    return super.onOptionsItemSelected(item);
        }
    }
}
