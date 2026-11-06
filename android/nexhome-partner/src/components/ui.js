import Feather from '@expo/vector-icons/Feather';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Keyboard, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text as RNText, TextInput, View } from 'react-native';

import { colors, fonts, placeholderGradient, radius } from '../theme';

/** Text in DM Sans; `weight` regular | medium | semibold | bold | extrabold. */
export function Text({ weight = 'regular', style, ...props }) {
  return <RNText {...props} style={[{ fontFamily: fonts[weight], color: colors.ink, fontSize: 14 }, style]} />;
}

/** The soft gradient that stands in for a missing image. */
export function GradientCover({ style }) {
  return <LinearGradient colors={placeholderGradient} locations={[0, 0.6, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={style} />;
}

/** An image from a URL, or the gradient when there is none or it fails to load. */
export function Cover({ uri, style }) {
  const [failed, setFailed] = useState(false);
  if (!uri || failed) return <GradientCover style={style} />;
  return <Image source={{ uri }} style={style} resizeMode="cover" onError={() => setFailed(true)} />;
}

/** variant: primary (green) | secondary (outlined blue) | blue | danger (outlined red) */
export function Button({ title, onPress, variant = 'primary', disabled, loading, icon, style }) {
  const look = BUTTONS[variant];
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.button,
        look.box,
        (disabled || loading) && { opacity: 0.55 },
        pressed && { opacity: 0.85 },
        style
      ]}
    >
      {loading ? (
        <ActivityIndicator color={look.text.color} />
      ) : (
        <>
          {icon ? <Feather name={icon} size={17} color={look.text.color} /> : null}
          <Text weight="bold" style={[{ fontSize: 15 }, look.text]}>
            {title}
          </Text>
        </>
      )}
    </Pressable>
  );
}

const BUTTONS = {
  primary: { box: { backgroundColor: colors.green }, text: { color: colors.white } },
  blue: { box: { backgroundColor: colors.blue }, text: { color: colors.white } },
  secondary: { box: { backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line }, text: { color: colors.blue } },
  danger: { box: { backgroundColor: colors.white, borderWidth: 1, borderColor: colors.redLine }, text: { color: colors.red } }
};

export function Card({ style, children, onPress }) {
  if (onPress) {
    return (
      <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.card, pressed && { opacity: 0.9 }, style]}>
        {children}
      </Pressable>
    );
  }
  return <View style={[styles.card, style]}>{children}</View>;
}

export function Tag({ label, fg, bg, style }) {
  return (
    <View style={[{ backgroundColor: bg, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999, alignSelf: 'flex-start' }, style]}>
      <Text weight="bold" style={{ color: fg, fontSize: 11.5 }}>
        {label}
      </Text>
    </View>
  );
}

/** A choice that is selected or not (sizes, dates, times, payment). */
export function Pick({ selected, onPress, children, style }) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.pick, selected ? styles.pickOn : null, style]}
    >
      {children}
    </Pressable>
  );
}

export function Field({ label, error, style, inputStyle, onFocus, ...props }) {
  const reveal = useContext(FormScrollContext);
  return (
    <View style={[{ gap: 6 }, style]}>
      {label ? (
        <Text weight="bold" style={{ fontSize: 13 }}>
          {label}
        </Text>
      ) : null}
      <TextInput
        placeholderTextColor="#8a9aad"
        {...props}
        onFocus={(event) => {
          reveal?.();
          onFocus?.(event);
        }}
        style={[styles.input, error ? { borderColor: colors.red } : null, props.multiline ? { minHeight: 96, textAlignVertical: 'top', paddingTop: 12 } : null, inputStyle]}
      />
      {error ? <Text style={{ color: colors.red, fontSize: 12 }}>{error}</Text> : null}
    </View>
  );
}

const FormScrollContext = createContext(null);

/**
 * A form's scrolling area that keeps the typed-in field above the keyboard (Android draws the app edge to edge, so the
 * window does not shrink by itself): the content is lifted by the keyboard's height, and the focused field is
 * scrolled into view when the keyboard opens and whenever another `Field` is focused. Everything down to the last
 * button can still be scrolled to while typing. Takes ScrollView props.
 */
export function FormScroll({ children, contentContainerStyle, onScroll, ...props }) {
  const scrollRef = useRef(null);
  const offset = useRef(0);
  const keyboardTop = useRef(null);

  const reveal = useCallback(() => {
    const input = TextInput.State.currentlyFocusedInput?.();
    if (!input?.measureInWindow || keyboardTop.current == null) return;
    // After the layout settles (the lift happens with the keyboard)
    setTimeout(() => {
      input.measureInWindow((x, y, width, height) => {
        const overlap = y + height + 24 - keyboardTop.current;
        if (overlap > 0) scrollRef.current?.scrollTo({ y: offset.current + overlap, animated: true });
      });
    }, 80);
  }, []);

  useEffect(() => {
    const show = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow', (event) => {
      keyboardTop.current = event.endCoordinates.screenY;
      reveal();
    });
    const hide = Keyboard.addListener(Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide', () => {
      keyboardTop.current = null;
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [reveal]);

  return (
    <FormScrollContext.Provider value={reveal}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior="padding">
        <ScrollView
          ref={scrollRef}
          keyboardShouldPersistTaps="handled"
          scrollEventThrottle={16}
          {...props}
          onScroll={(event) => {
            offset.current = event.nativeEvent.contentOffset.y;
            onScroll?.(event);
          }}
          contentContainerStyle={[{ paddingBottom: 24 }, contentContainerStyle]}
        >
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </FormScrollContext.Provider>
  );
}

/** A screen's top bar: back arrow (when `back`), title, optional right element. */
export function Header({ title, back = true, right }) {
  return (
    <View style={styles.header}>
      {back ? (
        <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={() => router.back()} style={styles.iconBox}>
          <Feather name="chevron-left" size={24} color={colors.navy} />
        </Pressable>
      ) : null}
      <Text weight="extrabold" style={{ fontSize: 18, color: colors.navy, flex: 1 }} numberOfLines={1}>
        {title}
      </Text>
      {right}
    </View>
  );
}

/**
 * The start-up screen: the same brand image as the native splash (assets/splash-brand.png: the logo, NexHome in
 * blue and green, "Better Living, Every Day"), so the app moves from the splash to its first screen without a jump.
 */
export function BrandSplash() {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.ground, padding: 40 }}>
      <Image source={require('../../assets/splash-brand.png')} style={{ width: 220, height: 118 }} resizeMode="contain" accessibilityLabel="NexHome. Better Living, Every Day" />
    </View>
  );
}

/** NexHome in the website's two colours with its caption below; `size` is the name's font size. */
export function BrandName({ size = 28, tag }) {
  return (
    <View style={{ alignItems: 'center' }}>
      <RNText style={{ fontFamily: fonts.extrabold, fontSize: size, letterSpacing: -0.4, lineHeight: size * 1.15 }}>
        <RNText style={{ color: colors.blue }}>Nex</RNText>
        <RNText style={{ color: colors.green }}>Home</RNText>
      </RNText>
      <Text weight="bold" style={{ fontSize: Math.max(10, Math.round(size * 0.34)), color: '#6b7280', letterSpacing: 0.8, marginTop: 2 }}>
        Better Living, Every Day
      </Text>
      {tag ? (
        <View style={{ marginTop: 8, backgroundColor: colors.greenSoft, borderWidth: 1, borderColor: '#cdebdc', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 4 }}>
          <Text weight="extrabold" style={{ fontSize: 11, color: colors.green, letterSpacing: 0.4 }}>
            {tag}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

export function Loading() {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 }}>
      <ActivityIndicator size="large" color={colors.blue} />
    </View>
  );
}

export function Empty({ icon = 'inbox', title, text, action }) {
  return (
    <View style={{ alignItems: 'center', padding: 32, gap: 8 }}>
      <View style={{ width: 60, height: 60, borderRadius: 30, backgroundColor: colors.blueTint, alignItems: 'center', justifyContent: 'center' }}>
        <Feather name={icon} size={26} color={colors.blue} />
      </View>
      <Text weight="bold" style={{ fontSize: 15, textAlign: 'center' }}>
        {title}
      </Text>
      {text ? <Text style={{ color: colors.muted, textAlign: 'center', fontSize: 13 }}>{text}</Text> : null}
      {action}
    </View>
  );
}

export function ErrorBox({ message }) {
  if (!message) return null;
  return (
    <View accessibilityRole="alert" style={{ backgroundColor: colors.redSoft, borderColor: colors.redLine, borderWidth: 1, borderRadius: 12, padding: 12 }}>
      <Text weight="bold" style={{ color: colors.red, fontSize: 13 }}>
        {message}
      </Text>
    </View>
  );
}

export function SectionTitle({ children, style }) {
  return (
    <Text weight="extrabold" style={[{ fontSize: 15, color: colors.navy }, style]}>
      {children}
    </Text>
  );
}

/** A label and its value on one line (details cards). */
export function Row({ label, value, strong, color }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
      <Text style={{ color: colors.muted, fontSize: 13.5 }}>{label}</Text>
      <Text weight={strong ? 'extrabold' : 'semibold'} style={{ fontSize: strong ? 16 : 13.5, color: color ?? (strong ? colors.navy : colors.ink), flexShrink: 1, textAlign: 'right' }}>
        {value}
      </Text>
    </View>
  );
}

/**
 * One choice among options (lookup values etc.) as chips: `options` [{ value, label }], `value`, `onChange(value)`.
 * With `multiple`, `value` is an array and each chip toggles.
 */
export function Choices({ label, options, value, onChange, multiple, error }) {
  const isOn = (v) => (multiple ? (value ?? []).includes(v) : value === v);
  const press = (v) => {
    if (!multiple) onChange(v);
    else onChange(isOn(v) ? (value ?? []).filter((x) => x !== v) : [...(value ?? []), v]);
  };
  return (
    <View style={{ gap: 8 }}>
      {label ? (
        <Text weight="bold" style={{ fontSize: 13 }}>
          {label}
        </Text>
      ) : null}
      <View accessibilityRole={multiple ? undefined : 'radiogroup'} style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {options.map((option) => {
          const on = isOn(option.value);
          return (
            <Pressable
              key={String(option.value)}
              accessibilityRole={multiple ? 'checkbox' : 'radio'}
              accessibilityState={multiple ? { checked: on } : { selected: on }}
              onPress={() => press(option.value)}
              style={[styles.chip, on ? styles.chipOn : null]}
            >
              {multiple && on ? <Feather name="check" size={14} color={colors.blue} /> : null}
              <Text weight="bold" style={{ fontSize: 13, color: on ? colors.blue : colors.ink }}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {error ? <Text style={{ color: colors.red, fontSize: 12 }}>{error}</Text> : null}
    </View>
  );
}

/** A tick box with its text. */
export function Check({ checked, onChange, label }) {
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked }} onPress={() => onChange(!checked)} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 44 }}>
      <View style={{ width: 24, height: 24, borderRadius: 7, borderWidth: 2, borderColor: checked ? colors.green : '#b8c6d4', backgroundColor: checked ? colors.green : colors.white, alignItems: 'center', justifyContent: 'center' }}>
        {checked ? <Feather name="check" size={15} color={colors.white} /> : null}
      </View>
      <Text weight="semibold" style={{ flex: 1 }}>
        {label}
      </Text>
    </Pressable>
  );
}

/** A coloured note: tone info (blue) | success (green) | warning (amber) | danger (red). */
export function Banner({ tone = 'info', icon, title, text, children }) {
  const look = {
    info: [colors.blueTint, colors.blue],
    success: [colors.greenSoft, colors.greenDark],
    warning: [colors.amberSoft, colors.amber],
    danger: [colors.redSoft, colors.red]
  }[tone];
  return (
    <View style={{ backgroundColor: look[0], borderRadius: 16, padding: 14, flexDirection: 'row', gap: 10 }}>
      <Feather name={icon ?? (tone === 'success' ? 'check-circle' : tone === 'info' ? 'info' : 'alert-circle')} size={20} color={look[1]} style={{ marginTop: 1 }} />
      <View style={{ flex: 1, gap: 4 }}>
        {title ? (
          <Text weight="bold" style={{ color: look[1] }}>
            {title}
          </Text>
        ) : null}
        {text ? <Text style={{ color: colors.ink, fontSize: 13, lineHeight: 19 }}>{text}</Text> : null}
        {children}
      </View>
    </View>
  );
}

/** A round photo, or the initials on blue. */
export function Avatar({ uri, name, size = 60 }) {
  if (uri) return <Cover uri={uri} style={{ width: size, height: size, borderRadius: size / 2 }} />;
  const letters = (name ?? '')
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: colors.blue, alignItems: 'center', justifyContent: 'center' }}>
      <Text weight="extrabold" style={{ color: colors.white, fontSize: size / 3 }}>
        {letters || '?'}
      </Text>
    </View>
  );
}

/** Two or more tabs under a screen title (Upcoming / Completed ...). */
/** Underlined tabs: `tabs` [key, label, count?]; a count shows as a small badge (blue on the chosen tab). */
export function Tabs({ tabs, value, onChange }) {
  return (
    <View accessibilityRole="tablist" style={{ flexDirection: 'row', gap: 20 }}>
      {tabs.map(([key, label, count]) => {
        const on = value === key;
        return (
          <Pressable
            key={key}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            accessibilityLabel={count === undefined ? label : `${label}, ${count}`}
            onPress={() => onChange(key)}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 5, paddingBottom: 11, borderBottomWidth: 3, borderBottomColor: on ? colors.blue : 'transparent' }}
          >
            <Text weight="bold" style={{ fontSize: 13, color: on ? colors.blue : colors.muted }}>
              {label}
            </Text>
            {count === undefined ? null : (
              <View style={{ minWidth: 18, height: 18, borderRadius: 9, paddingHorizontal: 5, alignItems: 'center', justifyContent: 'center', backgroundColor: on ? colors.blue : '#e8eef5' }}>
                <Text weight="extrabold" style={{ fontSize: 10, color: on ? colors.white : colors.muted }}>
                  {count}
                </Text>
              </View>
            )}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 50,
    borderRadius: radius.md,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8
  },
  card: { backgroundColor: colors.white, borderColor: colors.line, borderWidth: 1, borderRadius: radius.lg, padding: 16 },
  pick: { borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, borderRadius: 12, padding: 12 },
  pickOn: { borderWidth: 2, borderColor: colors.blue, backgroundColor: colors.blueSoft },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 12,
    paddingHorizontal: 14,
    fontFamily: fonts.regular,
    fontSize: 15,
    color: colors.ink,
    backgroundColor: colors.white
  },
  header: {
    height: 60,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 8,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.line
  },
  iconBox: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 44, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white },
  chipOn: { borderColor: colors.blue, backgroundColor: colors.blueSoft }
});
