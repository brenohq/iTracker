package itracker.sd;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.DateFormat;
import java.util.Date;

public class SelfHostedGPSTrackerActivity extends Activity implements LocationListener {

    private final static String CONNECTIVITY = "android.net.conn.CONNECTIVITY_CHANGE";

    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;

    SharedPreferences preferences;
    private EditText edit_url;
    private TextView text_gps_status;
    private TextView text_network_status;
    private ToggleButton button_toggle;
    private TextView text_running_since;
    private TextView last_server_response;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(SelfHostedGPSTrackerService.NOTIFICATION)) {
                String extra = intent.getStringExtra(SelfHostedGPSTrackerService.NOTIFICATION);
                if (extra != null) {
                    updateServerResponse();
                } else {
                    updateServiceStatus();
                }
            }
            if (action.equals(CONNECTIVITY)) {
                updateNetworkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(itracker.sd.R.layout.activity_self_hosted_gpstracker);

        edit_url = (EditText)findViewById(itracker.sd.R.id.edit_url);
        text_gps_status = (TextView)findViewById(itracker.sd.R.id.text_gps_status);
        text_network_status = (TextView)findViewById(itracker.sd.R.id.text_network_status);
        button_toggle = (ToggleButton)findViewById(itracker.sd.R.id.button_toggle);
        text_running_since = (TextView)findViewById(itracker.sd.R.id.text_running_since);
        last_server_response = (TextView)findViewById(itracker.sd.R.id.last_server_response);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.contains("URL") && ! preferences.getString("URL", "").equals("")) {
            edit_url.setText(preferences.getString("URL", getString(itracker.sd.R.string.hint_url)));
            edit_url.clearFocus();
        } else {
            edit_url.requestFocus();
        }
        edit_url.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("URL", s.toString().trim());
                editor.commit();
            }
        });

        registerReceiver(receiver, new IntentFilter(SelfHostedGPSTrackerService.NOTIFICATION));
        registerReceiver(receiver, new IntentFilter(SelfHostedGPSTrackerActivity.CONNECTIVITY));

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        int pref_gps_updates = Integer.parseInt(preferences.getString("pref_gps_updates", "30")); // seconds
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, pref_gps_updates * 1000, 1, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onProviderEnabled(LocationManager.GPS_PROVIDER);
        } else {
            onProviderDisabled(LocationManager.GPS_PROVIDER);
        }

        updateNetworkStatus();
        updateServiceStatus();
        updateServerResponse();

        if (SelfHostedGPSTrackerService.isRunning) {
            edit_url.setEnabled(false);
        } else {
            edit_url.setEnabled(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(itracker.sd.R.menu.activity_self_hosted_gpstracker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        switch (item.getItemId()) {
        case itracker.sd.R.id.menu_settings:
            i = new Intent(this, SelfHostedGPSTrackerPrefs.class);
            startActivity(i);
            break;
        default:
        }
        return super.onOptionsItemSelected(item);
    }


    // Método invocado quando botão rastrear é clicado

    public void onToggleClicked(View view) {
        Intent intent = new Intent(this, SelfHostedGPSTrackerService.class);
        if (((ToggleButton) view).isChecked()) {
            startService(intent);
            edit_url.setEnabled(false);
        } else {
            stopService(intent);
            edit_url.setEnabled(true);
        }
    }

    /* ---------------- GPS ---------------- */

    @Override
    public void onLocationChanged(Location location) {
    }

    // Desliga sensor GPS
    @Override
    public void onProviderDisabled(String provider) {
        text_gps_status.setText(getString(itracker.sd.R.string.text_gps_status_disabled));
        text_gps_status.setTextColor(Color.RED);
    }

    // Liga sensor GPS
    @Override
    public void onProviderEnabled(String provider) {
        text_gps_status.setText(getString(itracker.sd.R.string.text_gps_status_enabled));
        text_gps_status.setTextColor(Color.BLACK);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    /* ----------- Atualizações GPS -------------- */
    private void updateServiceStatus() {

        if (SelfHostedGPSTrackerService.isRunning) {
            Toast.makeText(this, getString(itracker.sd.R.string.toast_service_running), Toast.LENGTH_SHORT).show();
            button_toggle.setChecked(true);
            text_running_since.setText(getString(itracker.sd.R.string.text_running_since) + " "
                    + DateFormat.getDateTimeInstance().format(SelfHostedGPSTrackerService.runningSince.getTime()));
        } else {
            Toast.makeText(this, getString(itracker.sd.R.string.toast_service_stopped), Toast.LENGTH_SHORT).show();
            button_toggle.setChecked(false);
            if (preferences.contains("stoppedOn")) {
                long stoppedOn = preferences.getLong("stoppedOn", 0);
                if (stoppedOn > 0) {
                    text_running_since.setText(getString(itracker.sd.R.string.text_stopped_on) + " "
                            + DateFormat.getDateTimeInstance().format(new Date(stoppedOn)));
                } else {
                    text_running_since.setText(getText(itracker.sd.R.string.text_killed));
                }
            }
        }
    }


    // STATUS DA REDE
    private void updateNetworkStatus() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            text_network_status.setText(getString(itracker.sd.R.string.text_network_status_enabled));
            text_network_status.setTextColor(Color.BLACK);
        } else {
            text_network_status.setText(getString(itracker.sd.R.string.text_network_status_disabled));
            text_network_status.setTextColor(Color.RED);
        }
    }

    private void updateServerResponse() {
        if (SelfHostedGPSTrackerService.lastServerResponse != null) {
            last_server_response.setText(
                    Html.fromHtml(SelfHostedGPSTrackerService.lastServerResponse)
            );
        }
    }
}
