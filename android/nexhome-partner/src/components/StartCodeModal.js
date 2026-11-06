import Feather from '@expo/vector-icons/Feather';
import { useRef, useState } from 'react';
import { KeyboardAvoidingView, Modal, Pressable, TextInput, View } from 'react-native';

import { colors, fonts } from '../theme';
import { Button, Text } from './ui';

/**
 * Starting the work (V59): the partner types the customer's 4-digit start code; `onSubmit(code)` sends it
 * (POST /api/bookings/timer START with otp) and throws the backend's message when it is wrong ("Wrong start code. 4
 * tries left."), which shows here; on success the screen closes it.
 */
export default function StartCodeModal({ visible, onSubmit, onClose }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const input = useRef(null);

  const close = () => {
    setCode('');
    setError('');
    onClose();
  };

  const submit = async () => {
    if (code.length !== 4) {
      setError('Enter the 4-digit code.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await onSubmit(code);
      setCode('');
    } catch (err) {
      setError(err.message);
      setCode('');
      input.current?.focus();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close} onShow={() => setTimeout(() => input.current?.focus(), 150)}>
      <KeyboardAvoidingView behavior="padding" style={{ flex: 1, backgroundColor: 'rgba(11,49,95,0.45)', justifyContent: 'center', padding: 24 }}>
        <View style={{ backgroundColor: colors.white, borderRadius: 22, padding: 20, gap: 14 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ width: 40, height: 40, borderRadius: 12, backgroundColor: colors.greenSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Feather name="key" size={19} color={colors.green} />
            </View>
            <View style={{ flex: 1 }}>
              <Text weight="extrabold" style={{ fontSize: 17, color: colors.navy }}>
                Enter the start code
              </Text>
              <Text style={{ fontSize: 12.5, color: colors.muted }}>Ask the customer for the 4-digit code in their app.</Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel="Close" onPress={close} hitSlop={10}>
              <Feather name="x" size={20} color={colors.muted} />
            </Pressable>
          </View>

          {/* Four boxes over one hidden input */}
          <Pressable onPress={() => input.current?.focus()} style={{ flexDirection: 'row', justifyContent: 'center', gap: 12 }}>
            {[0, 1, 2, 3].map((i) => (
              <View
                key={i}
                style={{
                  width: 52,
                  height: 60,
                  borderRadius: 14,
                  borderWidth: 2,
                  borderColor: error ? colors.red : code.length === i ? colors.blue : colors.line,
                  backgroundColor: colors.ground,
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <Text weight="extrabold" style={{ fontSize: 24, color: colors.navy }}>
                  {code[i] ?? ''}
                </Text>
              </View>
            ))}
          </Pressable>
          <TextInput
            ref={input}
            value={code}
            onChangeText={(text) => {
              setCode(text.replace(/\D/g, '').slice(0, 4));
              if (error) setError('');
            }}
            keyboardType="number-pad"
            maxLength={4}
            accessibilityLabel="Start code"
            style={{ position: 'absolute', opacity: 0, height: 1, width: 1, fontFamily: fonts.regular }}
          />

          {error ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, justifyContent: 'center' }}>
              <Feather name="alert-circle" size={15} color={colors.red} />
              <Text weight="semibold" style={{ color: colors.red, fontSize: 13 }}>
                {error}
              </Text>
            </View>
          ) : null}

          <Button title="Verify and start work" icon="play" onPress={submit} loading={busy} disabled={code.length !== 4} />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
