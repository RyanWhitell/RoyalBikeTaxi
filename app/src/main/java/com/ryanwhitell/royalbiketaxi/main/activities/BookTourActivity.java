package com.ryanwhitell.royalbiketaxi.main.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Patterns;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ryanwhitell.royalbiketaxi.R;

import java.util.Calendar;

public class BookTourActivity extends AppCompatActivity {

    private EditText mName;
    private EditText mNumber;
    private EditText mLocation;
    private EditText mRequests;
    private TextView mDate;
    private TextView mTime;
    private TimePickerDialog mTimePicker;
    private DatePickerDialog mDatePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_tour);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        assert toolbar != null;
        toolbar.setTitle("Book a Tour");

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_request_dispatch);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendEmail();
            }
        });

        mName = (EditText) findViewById(R.id.editText_name);
        mNumber = (EditText) findViewById(R.id.editText_number);
        mLocation = (EditText) findViewById(R.id.editText_location);
        mRequests = (EditText) findViewById(R.id.editText_additional);
        mDate = (TextView) findViewById(R.id.textView_date);
        mTime = (TextView) findViewById(R.id.textView_time);

        TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                String stringMinute;
                String stringHour;
                String stringAmPm;

                if (minute <= 9) {
                    stringMinute = "0" + Integer.toString(minute);
                } else {
                    stringMinute = Integer.toString(minute);
                }
                if ((hourOfDay < 12) && (hourOfDay != 0)) {
                    stringAmPm = "AM";
                    stringHour = Integer.toString(hourOfDay);
                } else if (hourOfDay == 12) {
                    stringAmPm = "Noon";
                    stringHour = Integer.toString(hourOfDay);
                } else if (hourOfDay == 0) {
                    stringAmPm = "Midnight";
                    stringHour = Integer.toString(12);
                } else {
                    stringAmPm = "PM";
                    stringHour = Integer.toString(hourOfDay - 12);
                }

                String time = stringHour + ":" + stringMinute + " " + stringAmPm;
                mTime.setText(time);
            }
        };

        DatePickerDialog.OnDateSetListener onDateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                String day = Integer.toString(dayOfMonth);
                String month;

                switch (monthOfYear) {
                    case 0:
                        month = "January";
                        break;
                    case 1:
                        month = "February";
                        break;
                    case 2:
                        month = "March";
                        break;
                    case 3:
                        month = "April";
                        break;
                    case 4:
                        month = "May";
                        break;
                    case 5:
                        month = "June";
                        break;
                    case 6:
                        month = "July";
                        break;
                    case 7:
                        month = "August";
                        break;
                    case 8:
                        month = "September";
                        break;
                    case 9:
                        month = "October";
                        break;
                    case 10:
                        month = "November";
                        break;
                    case 11:
                        month = "December";
                        break;
                    default:
                        month = "";
                        break;
                }

                String date = month + " " + day;
                mDate.setText(date);
            }
        };

        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);


        mTimePicker = new TimePickerDialog(this, onTimeSetListener, hour, minute, false);
        mDatePicker = new DatePickerDialog(this, onDateSetListener, year, month, day);
    }

    private void sendEmail() {
        String name = mName.getText().toString();
        String number = mNumber.getText().toString();
        String location = mLocation.getText().toString();
        String date = mDate.getText().toString();
        String time = mTime.getText().toString();
        String requests = mRequests.getText().toString();

        if (name.equals("")) {
            Toast.makeText(this, "Please provide your name.", Toast.LENGTH_LONG).show();
        } else if (!Patterns.PHONE.matcher(number).matches()) {
            Toast.makeText(this, "Please provide a valid phone number.", Toast.LENGTH_LONG).show();
        } else if (location.equals("")) {
            Toast.makeText(this, "Please provide a location.", Toast.LENGTH_LONG).show();
        } else if (date.equals("Date")) {
            Toast.makeText(this, "Please provide a date.", Toast.LENGTH_LONG).show();
        } else if (time.equals("Time")) {
            Toast.makeText(this, "Please provide a time.", Toast.LENGTH_LONG).show();
        } else {
            if (requests.equals("")) {
                requests = "no requests";
            }

            String subject = "Tour Booking: " + date + " / " + time;
            String body = "Name: " + name + "\n" +
                    "Phone Number: " + number + "\n" +
                    "Location: " + location + "\n" +
                    "Requests : " + requests;

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("message/rfc822");
            emailIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{"example@royalbiketaxi.com"});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT   , body);
            try {
                startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "There are no email clients installed.", Toast.LENGTH_LONG).show();
            }
        }

    }

    public void setTime(View view) {
        mTimePicker.show();
    }

    public void setDate(View view) {
        mDatePicker.show();
    }

}
