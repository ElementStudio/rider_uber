package com.example.uberrider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.uberrider.Common.Common;
import com.example.uberrider.Model.RiderModel;
import com.example.uberrider.Utils.UserUtils;
import com.firebase.ui.auth.AuthMethodPickerLayout;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class SplashScreenActivity extends AppCompatActivity {

    private final static int LOGIN_REQUEST_CODE = 7171;
    private List<AuthUI.IdpConfig> providers;
    private FirebaseAuth firebaseAuth;
    private  FirebaseAuth.AuthStateListener listener;

    private ProgressBar progress_bar;

    FirebaseDatabase database;
    DatabaseReference riderInfoRef;

    @Override
    protected void onStart() {
        super.onStart();
        delaySplashScreen();
    }


    @Override
    protected void onStop() {
        if (firebaseAuth != null && listener != null)
            firebaseAuth.removeAuthStateListener(listener);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        progress_bar = findViewById(R.id.progress_bar);
        init();
    }

    private void init() {  
        database = FirebaseDatabase.getInstance(); 
        riderInfoRef = database.getReference(Common.RIDER_INFO_REFERENCE);
        
        providers = Arrays.asList(  
                new AuthUI.IdpConfig.PhoneBuilder().build(), 
                new AuthUI.IdpConfig.GoogleBuilder().build()
        );
        firebaseAuth = FirebaseAuth.getInstance(); 
        listener = myFirebaseAuth -> {
            FirebaseUser user = myFirebaseAuth.getCurrentUser(); 
            if (user!= null)  
            {
                //update token
                FirebaseMessaging.getInstance().getToken()
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(SplashScreenActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }).addOnSuccessListener(new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(String s) {
                                Log.d("TOKEN", s);
                                UserUtils.updateToken(SplashScreenActivity.this,s);
                            }
                        });

                checkUserFromFirebase();
            }  
            else
                showLoginLayout();
        };
    }

    private void showLoginLayout() {
        AuthMethodPickerLayout authMethodPickerLayout = new AuthMethodPickerLayout.Builder(
                R.layout.layout_sign_in)
                .setPhoneButtonId(R.id.btn_phone_sign_in)
                .setGoogleButtonId(R.id.btn_google_sign_in)
                .build();

        startActivityForResult(AuthUI.getInstance()
        .createSignInIntentBuilder()
        .setAuthMethodPickerLayout(authMethodPickerLayout)
        .setIsSmartLockEnabled(false)
        .setTheme(R.style.LoginTheme)
        .setAvailableProviders(providers)
        .build(),LOGIN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST_CODE) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (resultCode == RESULT_OK) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            }
            else {
                Toast.makeText(this, response.getError().getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkUserFromFirebase() {
        riderInfoRef.child(FirebaseAuth.getInstance().getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            RiderModel riderModel = snapshot.getValue(RiderModel.class);
                            goToHomeActivity(riderModel);
                        }
                        else {
                            showRegisterLayout();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SplashScreenActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showRegisterLayout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);

        View itemView = LayoutInflater.from(this).inflate(R.layout.layout_register,null);

        TextInputEditText edit_first_name = itemView.findViewById(R.id.edt_first_name);
        TextInputEditText edit_last_name = itemView.findViewById(R.id.edt_last_name);
        TextInputEditText edit_phone_number = itemView.findViewById(R.id.edit_phone_number);

        Button btn_continue = itemView.findViewById(R.id.btn_register);


        // Set data

        if (FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() != null && !TextUtils.isEmpty(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber()))
            edit_phone_number.setText(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());

        //set View
        builder.setView(itemView);
        AlertDialog dialog = builder.create();
        dialog.show();

        btn_continue.setOnClickListener(view ->  {
            if (TextUtils.isEmpty(edit_first_name.getText().toString()))
            {
                Toast.makeText(this, "Please enter first name", Toast.LENGTH_SHORT).show();
                return;
            }
            else if (TextUtils.isEmpty(edit_last_name.getText().toString()))  {
                Toast.makeText(this, "Please enter last name", Toast.LENGTH_SHORT).show();
                return;
            }

            else if (TextUtils.isEmpty(edit_phone_number.getText().toString()))  {
                Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            else {
                RiderModel model = new  RiderModel();
                model.setFirstName(edit_first_name.getText().toString());
                model.setLastName(edit_last_name.getText().toString());
                model.setPhoneNumber(edit_phone_number.getText().toString());


                riderInfoRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                        .setValue(model)
                        .addOnFailureListener(e -> {
                            dialog.dismiss();
                            Toast.makeText(SplashScreenActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        } )
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Registered Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            goToHomeActivity(model);

                        });
            }
        });
    }

    private void goToHomeActivity(RiderModel riderModel) {
        Common.currentRider = riderModel;
        startActivity(new Intent(this,HomeActivity.class));
        finish();
    }

    private void delaySplashScreen() {
        progress_bar.setVisibility(View.VISIBLE);

        Completable.timer(3, TimeUnit.SECONDS,
                AndroidSchedulers.mainThread()).subscribe(() ->
                firebaseAuth.addAuthStateListener(listener)
        );
    }
}