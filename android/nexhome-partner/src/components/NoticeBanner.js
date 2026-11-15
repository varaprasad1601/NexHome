import Feather from '@expo/vector-icons/Feather';
import { useEffect, useState } from 'react';
import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors } from '../theme';
import { Text } from './ui';

/**
 * The in-app notification popup: a card at the top of any screen with the notification's title and text, shown when
 * the phone's own notification banner cannot be (Expo Go, or notifications not allowed on the phone). Tap opens what it
 * is about, × or 6 seconds closes it; several in a row show one after another. `showNotice` is called by
 * components/LiveWatcher; this component is rendered once in _layout.
 */
const queue = [];
const listeners = new Set();

export function showNotice(notice) {
  queue.push(notice);
  listeners.forEach((listener) => listener());
}

export default function NoticeBanner() {
  const insets = useSafeAreaInsets();
  const [item, setItem] = useState(null);

  useEffect(() => {
    const listener = () => setItem((current) => current ?? queue.shift() ?? null);
    listeners.add(listener);
    return () => listeners.delete(listener);
  }, []);

  useEffect(() => {
    if (!item) return undefined;
    const timer = setTimeout(() => setItem(queue.shift() ?? null), 6000);
    return () => clearTimeout(timer);
  }, [item]);

  if (!item) return null;
  const high = item.priority === 'HIGH';
  const close = () => setItem(queue.shift() ?? null);
  return (
    <View pointerEvents="box-none" style={{ position: 'absolute', top: insets.top + 8, left: 12, right: 12, zIndex: 1001, elevation: 14 }}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${item.title}. ${item.body ?? ''}`}
        onPress={() => {
          close();
          item.onPress?.();
        }}
        style={{
          flexDirection: 'row',
          alignItems: 'flex-start',
          gap: 12,
          padding: 14,
          borderRadius: 16,
          backgroundColor: colors.white,
          borderWidth: 1,
          borderColor: high ? colors.red : colors.line,
          borderLeftWidth: 5,
          borderLeftColor: high ? colors.red : colors.blue,
          shadowColor: '#000',
          shadowOpacity: 0.18,
          shadowRadius: 12,
          shadowOffset: { width: 0, height: 5 }
        }}
      >
        <View style={{ width: 36, height: 36, borderRadius: 11, backgroundColor: high ? '#fdecec' : colors.blueSoft ?? '#e6eef9', alignItems: 'center', justifyContent: 'center' }}>
          <Feather name={item.icon ?? 'bell'} size={18} color={high ? colors.red : colors.blue} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text weight="extrabold" style={{ fontSize: 14, color: colors.navy }} numberOfLines={1}>
            {item.title}
          </Text>
          {item.body ? (
            <Text style={{ fontSize: 12.5, color: colors.ink, lineHeight: 17 }} numberOfLines={3}>
              {item.body}
            </Text>
          ) : null}
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Close" onPress={close} hitSlop={10}>
          <Feather name="x" size={16} color={colors.muted} />
        </Pressable>
      </Pressable>
    </View>
  );
}
