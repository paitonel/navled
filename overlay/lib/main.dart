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
  bool notif = false;
  String gps = 'off';
  Timer? timer;

  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 1), (_) => refresh());
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  Future<void> refresh() async {
    try {
      final r = await ch.invokeMapMethod<String, dynamic>('status');
      if (r == null || !mounted) return;
      setState(() {
        ble = (r['ble'] ?? '...').toString();
        raw = (r['raw'] ?? '').toString();
        notif = r['notif'] == true;
        gps = (r['gps'] ?? 'off').toString();
      });
    } catch (e) {
      if (mounted) setState(() => ble = 'status error: $e');
    }
  }

  Future<void> call(String m, [Map<String, int>? args]) => ch.invokeMethod(m, args);

  Widget btn(String label, VoidCallback f) =>
      FilledButton(onPressed: f, child: Text(label));

  Widget cmd(String label, int code, Color color) => FilledButton(
        style: FilledButton.styleFrom(backgroundColor: color),
        onPressed: () => call('cmd', {'code': code}),
        child: Text(label),
      );

  Widget tst(String label, int dir, int dist, [int speed = 0]) => FilledButton.tonal(
        onPressed: () => call('send', {'dir': dir, 'dist': dist, 'speed': speed}),
        child: Text(label),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('NavTurnLED')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('LED unit: $ble', style: const TextStyle(fontSize: 18)),
          Text('Notification access: ${notif ? "ON" : "OFF"}',
              style: TextStyle(fontSize: 18, color: notif ? Colors.greenAccent : Colors.orangeAccent)),
          const SizedBox(height: 12),
          Wrap(spacing: 8, runSpacing: 8, children: [
            btn('1. Allow Bluetooth', () => call('permissions')),
            btn('2. Notification access', () => call('notifSettings')),
            btn('3. Connect', () => call('connect')),
          ]),
          const SizedBox(height: 12),
          Text('Phone GPS speed: $gps', style: const TextStyle(fontSize: 18)),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            btn('Start GPS speed', () => call('gpsStart')),
            btn('Stop GPS speed', () => call('gpsStop')),
          ]),
          const Divider(height: 32),
          const Text('Unit control'),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            cmd('Unit ON', 20, Colors.green.shade700),
            cmd('Unit OFF', 21, Colors.red.shade700),
            cmd('Buzzer ON', 22, Colors.blue.shade700),
            cmd('Buzzer OFF', 23, Colors.orange.shade800),
          ]),
          const Divider(height: 32),
          const Text('Test (signal relay switches on when closer than 5 s at that speed)'),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            tst('Left 900 m', 1, 900, 60),
            tst('Left 400 m', 1, 400, 60),
            tst('Left 150 m @60', 1, 150, 60),
            tst('Left 60 m @60', 1, 60, 60),
            tst('Right 900 m', 2, 900, 60),
            tst('Right 150 m @60', 2, 150, 60),
            tst('Right 60 m @60', 2, 60, 60),
            tst('Right 100 m @100', 2, 100, 100),
            tst('Straight', 3, 0),
            tst('U-turn 100 m', 4, 100),
            tst('Roundabout left', 5, 200),
            tst('Roundabout straight', 6, 200),
            tst('Roundabout right', 7, 200),
            tst('Arrived', 8, 0),
            tst('Faster route', 9, 0),
            tst('Hazard', 10, 0),
            tst('Speed 70 km/h', 12, 0, 70),
            tst('Speed 100 km/h', 12, 0, 100),
            tst('Speed 130 km/h', 12, 0, 130),
            tst('Clear', 0, 0),
          ]),
          const Divider(height: 32),
          const Text('Last navigation notification text:'),
          const SizedBox(height: 6),
          Text(raw, style: const TextStyle(color: Colors.white70)),
        ],
      ),
    );
  }
}

