import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const NavApp());

class NavApp extends StatelessWidget {
  const NavApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'NavTurnLED',
        theme: ThemeData.dark(useMaterial3: true),
        home: const HomePage(),
      );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  static const ch = MethodChannel('navled');
  String ble = '...';
  String raw = '';
  String gps = 'no speed yet';
  String gpsStatus = 'off';
  String log = '';
  bool notif = false;
  bool connected = false;
  Timer? timer;
  Timer? speedTimer;
  int? testKmh;

  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 1), (_) => refresh());
  }

  @override
  void dispose() {
    timer?.cancel();
    speedTimer?.cancel();
    super.dispose();
  }

  Future<void> refresh() async {
    try {
      final r = await ch.invokeMapMethod<String, dynamic>('status');
      if (r == null || !mounted) return;
      setState(() {
        ble = (r['ble'] ?? '...').toString();
        connected = r['connected'] == true;
        raw = (r['raw'] ?? '').toString();
        notif = r['notif'] == true;
        gps = (r['gps'] ?? 'no speed yet').toString();
        gpsStatus = (r['gpsStatus'] ?? 'off').toString();
        log = (r['log'] ?? '').toString();
      });
    } catch (e) {
      if (mounted) setState(() => ble = 'status error: $e');
    }
  }

  void toast(String t) {
    if (!mounted) return;
    final m = ScaffoldMessenger.of(context);
    m.hideCurrentSnackBar();
    m.showSnackBar(SnackBar(content: Text(t), duration: const Duration(seconds: 2)));
  }

  // Calls the phone side and tells you whether the packet really went out.
  Future<void> act(String method, Map<String, int> args, String okText) async {
    try {
      final ok = (await ch.invokeMethod<bool>(method, args)) ?? false;
      toast(ok ? okText : 'Not sent: the LED unit is not connected. Tap 3. Connect.');
    } catch (e) {
      toast('Error: $e');
    }
  }

  Future<void> plain(String method) async {
    try {
      await ch.invokeMethod(method);
    } catch (e) {
      toast('Error: $e');
    }
  }

  void startSpeedTest(int kmh) {
    speedTimer?.cancel();
    int n = 0;
    setState(() => testKmh = kmh);
    Future<void> tick() async {
      try {
        final ok = (await ch.invokeMethod<bool>('speed', {'kmh': kmh})) ?? false;
        if (!ok && n == 0) toast('Not sent: the LED unit is not connected.');
      } catch (_) {}
    }

    tick();
    speedTimer = Timer.periodic(const Duration(seconds: 1), (t) {
      n++;
      if (n >= 20) {
        stopSpeedTest();
        return;
      }
      tick();
    });
  }

  void stopSpeedTest() {
    speedTimer?.cancel();
    speedTimer = null;
    if (mounted) setState(() => testKmh = null);
  }

  Widget head(String t) => Padding(
        padding: const EdgeInsets.only(top: 22, bottom: 8),
        child: Text(t, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
      );

  Widget btn(String label, VoidCallback f) => FilledButton(onPressed: f, child: Text(label));

  Widget cmd(String label, int code, Color color, [int value = 0]) => FilledButton(
        style: FilledButton.styleFrom(backgroundColor: color),
        onPressed: () => act('cmd', {'code': code, 'value': value}, '$label sent'),
        child: Text(label),
      );

  Widget tst(String label, int dir, int dist,
          [int speed = 0, int exit = 0, int warn = 0, int variant = 0]) =>
      FilledButton.tonal(
        onPressed: () => act(
          'send',
          {'dir': dir, 'dist': dist, 'speed': speed, 'exit': exit, 'warn': warn, 'variant': variant},
          '$label sent',
        ),
        child: Text(label),
      );

  Widget spd(int kmh) => FilledButton.tonal(
        onPressed: () => startSpeedTest(kmh),
        child: Text('$kmh km/h'),
      );

  Widget wrap(List<Widget> c) => Wrap(spacing: 8, runSpacing: 8, children: c);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('NavTurnLED')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(children: [
            Icon(Icons.circle, size: 14, color: connected ? Colors.greenAccent : Colors.orangeAccent),
            const SizedBox(width: 8),
            Expanded(child: Text('LED unit: $ble', style: const TextStyle(fontSize: 17))),
          ]),
          const SizedBox(height: 4),
          Text('Notification access: ${notif ? "ON" : "OFF"}',
              style: TextStyle(fontSize: 17, color: notif ? Colors.greenAccent : Colors.orangeAccent)),
          const SizedBox(height: 12),
          wrap([
            btn('1. Allow Bluetooth', () => plain('permissions')),
            btn('2. Notification access', () => plain('notifSettings')),
            btn('3. Connect', () => plain('connect')),
          ]),

          head('Phone GPS speed'),
          Text('Speed: $gps', style: const TextStyle(fontSize: 16)),
          Text('Status: $gpsStatus', style: const TextStyle(color: Colors.white70)),
          const SizedBox(height: 8),
          wrap([
            btn('Start GPS speed', () async {
              try {
                final ok = (await ch.invokeMethod<bool>('gpsStart')) ?? false;
                toast(ok ? 'Starting GPS speed ...' : 'Location permission needed: accept it, then tap again.');
              } catch (e) {
                toast('Error: $e');
              }
            }),
            btn('Stop GPS speed', () => plain('gpsStop')),
          ]),

          head('Unit control'),
          wrap([
            cmd('Unit ON', 20, Colors.green.shade700),
            cmd('Unit OFF', 21, Colors.red.shade700),
            cmd('Buzzer ON', 22, Colors.blue.shade700),
            cmd('Buzzer OFF', 23, Colors.orange.shade800),
          ]),

          head('Speed limit (saved in the unit)'),
          wrap([
            cmd('Limit OFF', 24, Colors.grey.shade700, 0),
            cmd('50', 24, Colors.teal.shade700, 50),
            cmd('60', 24, Colors.teal.shade700, 60),
            cmd('80', 24, Colors.teal.shade700, 80),
            cmd('100', 24, Colors.teal.shade700, 100),
            cmd('120', 24, Colors.teal.shade700, 120),
          ]),

          head('Speed test (sends a fake speed for 20 s)'),
          const Text(
            'Set a limit above first. With the GPS module the fake speed is used only while the module has no fix.',
            style: TextStyle(color: Colors.white70, fontSize: 12),
          ),
          const SizedBox(height: 8),
          wrap([
            spd(40), spd(70), spd(90), spd(100), spd(130),
            FilledButton(onPressed: stopSpeedTest, child: const Text('Stop test')),
          ]),
          if (testKmh != null)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text('Sending $testKmh km/h ...', style: const TextStyle(color: Colors.greenAccent)),
            ),

          head('Turns (distance shown in the name)'),
          wrap([
            tst('Left 900 m', 1, 900, 60),
            tst('Left 450 m', 1, 450, 60),
            tst('Left 200 m', 1, 200, 60),
            tst('Left 120 m', 1, 120, 60),
            tst('Left 70 m', 1, 70, 60),
            tst('Left 50 m', 1, 50, 60),
            tst('Right 900 m', 2, 900, 60),
            tst('Right 450 m', 2, 450, 60),
            tst('Right 120 m', 2, 120, 60),
            tst('Right 50 m', 2, 50, 60),
            tst('Sharp right 450 m', 2, 450, 60, 0, 0, 2),
            tst('Merge left 450 m', 15, 450, 60),
            tst('Merge right 450 m', 16, 450, 60),
            tst('Straight', 3, 0),
          ]),

          head('Slight and keep'),
          wrap([
            tst('Slight left 400 m', 1, 400, 60, 0, 0, 1),
            tst('Slight left 140 m', 1, 140, 60, 0, 0, 1),
            tst('Slight right 140 m', 2, 140, 60, 0, 0, 1),
            tst('Keep left 400 m', 13, 400, 60),
            tst('Keep left 140 m', 13, 140, 60),
            tst('Keep right 140 m', 14, 140, 60),
          ]),

          head('Roundabout and U-turn'),
          wrap([
            tst('Exit 1, 450 m', 5, 450, 60, 1),
            tst('Exit 2, 450 m', 6, 450, 60, 2),
            tst('Exit 3, 450 m', 7, 450, 60, 3),
            tst('Exit 2, 100 m', 6, 100, 60, 2),
            tst('U-turn 450 m', 4, 450, 60),
            tst('U-turn 100 m', 4, 100, 60),
          ]),

          head('Road warnings (red)'),
          wrap([
            tst('Police 300 m', 10, 300, 0, 0, 1),
            tst('Camera 300 m', 10, 300, 0, 0, 2),
            tst('Pothole 100 m', 10, 100, 0, 0, 3),
            tst('Hazard', 10, 0, 0, 0, 4),
            tst('Accident 400 m', 10, 400, 0, 0, 5),
            tst('Roadworks 400 m', 10, 400, 0, 0, 6),
            tst('Traffic jam 800 m', 10, 800, 0, 0, 7),
          ]),

          head('Other'),
          wrap([
            tst('Arrived', 8, 0),
            tst('Faster route', 9, 0),
            tst('Clear', 0, 0),
          ]),

          head('Last navigation notification'),
          Text(raw, style: const TextStyle(color: Colors.white70)),
          head('Notification log (last 20, newest first)'),
          Text(log.isEmpty ? '(nothing yet)' : log,
              style: const TextStyle(color: Colors.white70, fontSize: 12)),
        ],
      ),
    );
  }
}

