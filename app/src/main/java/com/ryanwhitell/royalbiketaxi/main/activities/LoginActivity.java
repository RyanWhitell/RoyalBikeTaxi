package com.ryanwhitell.royalbiketaxi.main.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;
import com.ryanwhitell.royalbiketaxi.main.model.Driver;
import com.ryanwhitell.royalbiketaxi.main.services.DriverService;

import java.util.ArrayList;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    // Login fields
    EditText mName;
    EditText mPassword;

    // Driver list
    ArrayList<Driver> mDrivers;

    //******* ACTIVITY LIFECYCLE *******//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mName = (EditText) findViewById(R.id.text_view_name);
        mPassword = (EditText) findViewById(R.id.text_view_password);

        mDrivers = new ArrayList<>();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Initialize Database and driver array
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference databaseRefDrivers = database.getReference("Drivers");
        databaseRefDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> drivers = (Map<String, Object>) dataSnapshot.getValue();

                    for (Map.Entry<String, Object> driver: drivers.entrySet()) {
                        Driver newDriver = new Driver(driver.getKey(),
                                ((Map<String, Object>) driver.getValue()).get("password").toString(),
                                ((Map<String, Object>) driver.getValue()).get("phoneNumber").toString());

                        mDrivers.add(newDriver);
                    }
                } else {
                    mDrivers = null;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(getApplicationContext(), "There was a database error!", Toast.LENGTH_LONG).show();
            }
        });
    }

    //******* LOGIN LOGIC *******//
    public void login(View view) {

        String name = mName.getText().toString();
        String pass = mPassword.getText().toString();

        if (mDrivers != null) {
            if (checkName(name)) {
                if (checkPassword(pass, name)) {
                    Intent intent = new Intent(this, DriverActivity.class);
                    DriverService.sDriverName = name;
                    String number = getNumber(name);
                    DriverService.sDriverNumber = number;
                    startActivity(intent);
                    this.finish();
                } else {
                    Toast.makeText(getApplicationContext(), "Invalid password", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Invalid name", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "There are no drivers in the database", Toast.LENGTH_LONG).show();
        }
    }

    public boolean checkName(String name) {
        for (Driver driver : mDrivers) {
            if (name.equals(driver.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPassword(String pass, String name) {
        for (Driver driver : mDrivers) {
            if ((pass.equals(driver.getPassword())) && (name.equals(driver.getName()))) {
                return true;
            }
        }
        return false;
    }

    public String getNumber(String name) {
        for (Driver driver : mDrivers) {
            if (name.equals(driver.getName())) {
                return driver.getPhoneNumber();
            }
        }
        return "0000000000";
    }

}
