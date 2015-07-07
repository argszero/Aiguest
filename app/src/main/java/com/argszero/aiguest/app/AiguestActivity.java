package com.argszero.aiguest.app;

import android.app.Activity;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.baidu.mobads.AdView;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AiguestActivity extends Activity {

    public static final String AI_GUEST = "AI_GUEST";

    private static class Progress {
        private Activity activity;
        private TextView textView;
        private StringBuilder sb;

        public Progress(Activity activity, TextView textView, StringBuilder sb) {
            this.activity = activity;
            this.textView = textView;
            this.sb = sb;
        }

        private void report(String msg) {
            sb.append(msg).append("\n");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(sb.toString());
                }
            });
        }

        public void report(Throwable e) {
            StringWriter wr = new StringWriter();
            PrintWriter writer = new PrintWriter(wr);
            e.printStackTrace(writer);
            report(wr.toString());
            Log.e("", "", e);
        }

        public void reset() {
            sb.delete(0, sb.length());
            report(activity.getApplication().getString(R.string.user_tip));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences sharedPreferences = getSharedPreferences("aiguest", Activity.MODE_PRIVATE);
        String name = sharedPreferences.getString("name", "");
        String pwd = sharedPreferences.getString("pwd", "");
        setContentView(R.layout.activity_aiguest);
        Button but = (Button) this.findViewById(R.id.button);
        final Progress progress = new Progress(this, (TextView) this.findViewById(R.id.textView2), new StringBuilder());
        final EditText nameText = (EditText) this.findViewById(R.id.nameText);
        final EditText pwdText = (EditText) this.findViewById(R.id.pwdText);
        nameText.setText(name);
        pwdText.setText(pwd);
        final WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        but.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    progress.reset();
                    String authString = nameText.getText().toString()
                            + ":"
                            + pwdText.getText().toString();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("name", nameText.getText().toString());
                    editor.putString("pwd", pwdText.getText().toString());
                    editor.commit();
                    progress.report("getting new password,please wait 2 minutes");
                    String pwd = new GetWifiPwd().execute(progress, authString).get();
                    if (pwd != null) {
                        progress.report("star scan wifi,please wait for 2 minutes");
                        WifiScanReceiver wifiScanReceiver = new WifiScanReceiver(progress, pwd);
                        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                        wifiManager.startScan();
                    } else {
                        progress.report("cannot get password!");
                    }
                } catch (InterruptedException e) {
                    progress.report(e);
                } catch (ExecutionException e) {
                    progress.report(e);
                }
            }
        });
        RelativeLayout adRowLayout = (RelativeLayout) this.findViewById(R.id.ad_row);
        AdView adView = new AdView(this);
        adRowLayout.addView(adView);
    }


    private class GetWifiPwd extends AsyncTask<Object, Integer, String> {
        @Override
        protected String doInBackground(Object... params) {
            Progress progress = (Progress) params[0];
            String authString = (String) params[1];
            try {
                byte[] authEncBytes = Base64.encode(authString.getBytes(), Base64.DEFAULT);
                String authStringEnc = new String(authEncBytes);
                URL url = new URL("http://home.asiainfo.com/AIPRT/help/ai_guest.aspx");
                URLConnection urlConnection = url.openConnection();
                urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
                InputStream is = urlConnection.getInputStream();
                String html2 = IOUtils.toString(is);
                is.close();
                Pattern pattern = Pattern.compile("[\\s\\S]*.*id=\"lblpass\">([^<]*)</label>.*[\\s\\S]*");
                Matcher matcher = pattern.matcher(html2);
                if (matcher.matches()) {
                    String pwd = matcher.group(1);
                    progress.report("get password:" + pwd);
                    return pwd;
                } else {
                    progress.report(getApplication().getString(R.string.auth_failed));
                }
            } catch (FileNotFoundException e) {
                progress.report(getApplication().getString(R.string.auth_failed));
            } catch (IOException e) {
                progress.report(getApplication().getString(R.string.connect_failed));
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_aiguest, menu);
        return true;
    }


    private class WifiScanReceiver extends BroadcastReceiver {
        private final Progress progress;
        private String pwd;

        public WifiScanReceiver(Progress progress, String pwd) {
            this.progress = progress;
            this.pwd = pwd;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            final WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> scanResults = wifiManager.getScanResults();
            ScanResult target = null;
            for (ScanResult scanResult : scanResults) {
                if (AI_GUEST.equals(scanResult.SSID)) {
                    target = scanResult;
                }
            }

            if (target == null) {
                progress.report("finding..");
            } else {
                progress.report("find it");
                WifiConfiguration config = new WifiConfiguration();
                config.SSID = "\"" + target.SSID + "\"";
                config.status = WifiConfiguration.Status.ENABLED;
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                int netId = wifiManager.addNetwork(config);
                progress.report("netId:" + netId);
                wifiManager.enableNetwork(netId, true);
                wifiManager.setWifiEnabled(true);
                progress.report("begin to change wifi pwd");


                WifiConfiguration targetWifiConf = null;
                int maxPriority = -1;
                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();

                for (WifiConfiguration conf : configuredNetworks) {
                    maxPriority = Math.max(maxPriority, conf.priority);
                    if ("\"AI_GUEST\"".equals(conf.SSID)) {
                        targetWifiConf = conf;
                        progress.report("find wifi: \"AI_GUEST\"");

                        conf.allowedGroupCiphers.clear();
                        conf.allowedPairwiseCiphers.clear();
                        conf.allowedProtocols.clear();
                        if (conf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                            if (pwd.matches("[0-9A-Fa-f]{64}")) {
                                conf.preSharedKey = pwd;
                            } else {
                                conf.preSharedKey = '"' + pwd + '"';
                            }
                        }

                    }
                }
                if (targetWifiConf != null) {
                    targetWifiConf.priority = maxPriority + 1;
                    final int networkId = wifiManager.updateNetwork(targetWifiConf);
                    if (networkId == -1) {
                        progress.report("update wifi password failed");
                    } else {
                        progress.report("update wifi password success");
                    }
                    wifiManager.disconnect();
                    wifiManager.reassociate();
                } else {
                    progress.report("cannot find wifi: \"AI_GUEST\"");
                }
            }
        }
    }
}
