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
    final r = await ch.invokeMapMethod<String, dynamic>('status');
    if (r == null || !mounted) return;
    setState(() {
      ble = r['ble'] as String;
      raw = r['raw'] as String;
      notif = r['notif'] as bool;
    });
  }

  Future<void> call(String m, [Map<String, int>? args]) => ch.invokeMethod(m, args);

  Widget btn(String label, VoidCallback f) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: FilledButton(onPressed: f, child: Text(label)),
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
          btn('1. Allow Bluetooth', () => call('permissions')),
          btn('2. Turn on notification access', () => call('notifSettings')),
          btn('3. Connect to NavTurnLED', () => call('connect')),
          const Divider(height: 32),
          const Text('Test the LEDs'),
          Row(children: [
            Expanded(child: btn('Left 100 m', () => call('send', {'dir': 1, 'dist': 100}))),
            const SizedBox(width: 8),
            Expanded(child: btn('Right 100 m', () => call('send', {'dir': 2, 'dist': 100}))),
          ]),
          Row(children: [
            Expanded(child: btn('Left 250 m', () => call('send', {'dir': 1, 'dist': 250}))),
            const SizedBox(width: 8),
            Expanded(child: btn('Right 250 m', () => call('send', {'dir': 2, 'dist': 250}))),
          ]),
          btn('Clear', () => call('send', {'dir': 0, 'dist': 0})),
          const Divider(height: 32),
          const Text('Last Google Maps notification text:'),
          const SizedBox(height: 6),
          Text(raw, style: const TextStyle(color: Colors.white70)),
        ],
      ),
    );
  }
}
