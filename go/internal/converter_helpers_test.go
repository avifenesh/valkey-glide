// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"reflect"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// --- ConvertToInt64 ---

func TestConvertToInt64_Int64(t *testing.T) {
	result, err := ConvertToInt64(int64(42))
	assert.NoError(t, err)
	assert.Equal(t, int64(42), result)
}

func TestConvertToInt64_Int(t *testing.T) {
	result, err := ConvertToInt64(int(99))
	assert.NoError(t, err)
	assert.Equal(t, int64(99), result)
}

func TestConvertToInt64_Float64(t *testing.T) {
	result, err := ConvertToInt64(float64(3.7))
	assert.NoError(t, err)
	assert.Equal(t, int64(3), result) // truncates
}

func TestConvertToInt64_String(t *testing.T) {
	result, err := ConvertToInt64("12345")
	assert.NoError(t, err)
	assert.Equal(t, int64(12345), result)
}

func TestConvertToInt64_NegativeString(t *testing.T) {
	result, err := ConvertToInt64("-100")
	assert.NoError(t, err)
	assert.Equal(t, int64(-100), result)
}

func TestConvertToInt64_InvalidString(t *testing.T) {
	_, err := ConvertToInt64("not_a_number")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "cannot convert string")
}

func TestConvertToInt64_UnsupportedType(t *testing.T) {
	_, err := ConvertToInt64([]int{1, 2, 3})
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "cannot convert")
}

func TestConvertToInt64_NilValue(t *testing.T) {
	_, err := ConvertToInt64(nil)
	assert.Error(t, err)
}

func TestConvertToInt64_ZeroValues(t *testing.T) {
	result, err := ConvertToInt64(int64(0))
	assert.NoError(t, err)
	assert.Equal(t, int64(0), result)

	result, err = ConvertToInt64("0")
	assert.NoError(t, err)
	assert.Equal(t, int64(0), result)

	result, err = ConvertToInt64(float64(0))
	assert.NoError(t, err)
	assert.Equal(t, int64(0), result)
}

func TestConvertToInt64_MaxInt64(t *testing.T) {
	result, err := ConvertToInt64(int64(9223372036854775807))
	assert.NoError(t, err)
	assert.Equal(t, int64(9223372036854775807), result)
}

// --- GetType ---

func TestGetType_String(t *testing.T) {
	typ := GetType[string]()
	assert.Equal(t, reflect.TypeOf(""), typ)
}

func TestGetType_Int64(t *testing.T) {
	typ := GetType[int64]()
	assert.Equal(t, reflect.TypeOf(int64(0)), typ)
}

func TestGetType_Float64(t *testing.T) {
	typ := GetType[float64]()
	assert.Equal(t, reflect.TypeOf(float64(0)), typ)
}

// --- mapConverter ---

func TestMapConverter_SimpleStringMap(t *testing.T) {
	input := map[string]any{"a": "hello", "b": "world"}
	converter := mapConverter[string]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	expected := map[string]string{"a": "hello", "b": "world"}
	assert.Equal(t, expected, result)
}

func TestMapConverter_NilNotAllowed(t *testing.T) {
	converter := mapConverter[string]{nil, false}
	_, err := converter.convert(nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type received: nil")
}

func TestMapConverter_NilAllowed(t *testing.T) {
	converter := mapConverter[string]{nil, true}
	result, err := converter.convert(nil)
	assert.NoError(t, err)
	assert.Nil(t, result)
}

func TestMapConverter_TypeMismatch(t *testing.T) {
	input := map[string]any{"a": int64(42)}
	converter := mapConverter[string]{nil, false}
	_, err := converter.convert(input)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type of map element")
}

func TestMapConverter_EmptyMap(t *testing.T) {
	input := map[string]any{}
	converter := mapConverter[string]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	assert.Equal(t, map[string]string{}, result)
}

func TestMapConverter_Float64Values(t *testing.T) {
	input := map[string]any{"score1": float64(1.5), "score2": float64(2.7)}
	converter := mapConverter[float64]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	expected := map[string]float64{"score1": 1.5, "score2": 2.7}
	assert.Equal(t, expected, result)
}

func TestMapConverter_NestedWithArrayConverter(t *testing.T) {
	input := map[string]any{
		"key1": []any{"a", "b"},
		"key2": []any{"c"},
	}
	converter := mapConverter[[]string]{
		arrayConverter[string]{nil, false},
		false,
	}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	resultMap := result.(map[string][]string)
	assert.Equal(t, []string{"a", "b"}, resultMap["key1"])
	assert.Equal(t, []string{"c"}, resultMap["key2"])
}

// --- arrayConverter ---

func TestArrayConverter_SimpleStrings(t *testing.T) {
	input := []any{"hello", "world"}
	converter := arrayConverter[string]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	assert.Equal(t, []string{"hello", "world"}, result)
}

func TestArrayConverter_NilNotAllowed(t *testing.T) {
	converter := arrayConverter[string]{nil, false}
	_, err := converter.convert(nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type received: nil")
}

func TestArrayConverter_NilAllowed(t *testing.T) {
	converter := arrayConverter[string]{nil, true}
	result, err := converter.convert(nil)
	assert.NoError(t, err)
	assert.Nil(t, result)
}

func TestArrayConverter_TypeMismatch(t *testing.T) {
	input := []any{"hello", int64(42)}
	converter := arrayConverter[string]{nil, false}
	_, err := converter.convert(input)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected type of array element")
}

func TestArrayConverter_EmptyArray(t *testing.T) {
	input := []any{}
	converter := arrayConverter[string]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	assert.Equal(t, []string{}, result)
}

func TestArrayConverter_Int64Values(t *testing.T) {
	input := []any{int64(1), int64(2), int64(3)}
	converter := arrayConverter[int64]{nil, false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	assert.Equal(t, []int64{1, 2, 3}, result)
}

func TestArrayConverter_Nested2DStrings(t *testing.T) {
	input := []any{
		[]any{"a", "b"},
		[]any{"c", "d"},
	}
	converter := arrayConverter[[]string]{
		arrayConverter[string]{nil, false},
		false,
	}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	expected := [][]string{{"a", "b"}, {"c", "d"}}
	assert.Equal(t, expected, result)
}

func TestArrayConverter_NestedWithNilInner(t *testing.T) {
	// When inner converter returns nil and canBeNil is true
	input := []any{nil, []any{"a"}}
	converter := arrayConverter[[]string]{
		arrayConverter[string]{nil, true},
		false,
	}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	resultSlice := result.([][]string)
	assert.Len(t, resultSlice, 2)
	assert.Nil(t, resultSlice[0]) // nil element
	assert.Equal(t, []string{"a"}, resultSlice[1])
}

// --- keyValuesConverter ---

func TestKeyValuesConverter_ValidInput(t *testing.T) {
	input := map[string]any{
		"key1": []any{"val1", "val2"},
		"key2": []any{"val3"},
	}
	converter := keyValuesConverter{false}
	result, err := converter.convert(input)
	assert.NoError(t, err)
	assert.Len(t, result, 2)

	// Build a map for order-independent check
	resultMap := make(map[string][]string)
	for _, kv := range result {
		resultMap[kv.Key] = kv.Values
	}
	assert.Equal(t, []string{"val1", "val2"}, resultMap["key1"])
	assert.Equal(t, []string{"val3"}, resultMap["key2"])
}

// --- ReadValue / ReadResult ---

func TestReadValue_ExistingField(t *testing.T) {
	data := map[string]any{"name": "test"}
	var name string
	ReadValue(data, "name", &name)
	assert.Equal(t, "test", name)
}

func TestReadValue_MissingField(t *testing.T) {
	data := map[string]any{"other": "value"}
	var name string
	ReadValue(data, "name", &name)
	assert.Equal(t, "", name) // zero value, no panic
}

func TestReadValue_WrongType(t *testing.T) {
	data := map[string]any{"count": "not_an_int"}
	var count int64
	ReadValue(data, "count", &count)
	assert.Equal(t, int64(0), count) // zero value, no panic
}

func TestReadResult_ExistingField(t *testing.T) {
	data := map[string]any{"score": int64(42)}
	var result models.Result[int64]
	ReadResult(data, "score", &result)
	assert.False(t, result.IsNil())
	assert.Equal(t, int64(42), result.Value())
}

func TestReadResult_NilField(t *testing.T) {
	data := map[string]any{"score": nil}
	var result models.Result[int64]
	ReadResult(data, "score", &result)
	assert.True(t, result.IsNil())
}

func TestReadResult_MissingField(t *testing.T) {
	data := map[string]any{}
	var result models.Result[int64]
	ReadResult(data, "score", &result)
	assert.True(t, result.IsNil())
}

// --- ParseLCSMatchedPositions ---

func TestParseLCSMatchedPositions_Nil(t *testing.T) {
	result, err := ParseLCSMatchedPositions(nil)
	assert.NoError(t, err)
	assert.Equal(t, []models.LCSMatchedPosition{}, result)
}

func TestParseLCSMatchedPositions_InvalidType(t *testing.T) {
	_, err := ParseLCSMatchedPositions("not_an_array")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "expected matches to be an array")
}

func TestParseLCSMatchedPositions_EmptyArray(t *testing.T) {
	result, err := ParseLCSMatchedPositions([]any{})
	assert.NoError(t, err)
	assert.Empty(t, result)
}

func TestParseLCSMatchedPositions_ValidWithoutMatchLen(t *testing.T) {
	input := []any{
		[]any{
			[]any{int64(4), int64(7)}, // key1
			[]any{int64(5), int64(8)}, // key2
		},
	}
	result, err := ParseLCSMatchedPositions(input)
	assert.NoError(t, err)
	assert.Len(t, result, 1)
	assert.Equal(t, int64(4), result[0].Key1.Start)
	assert.Equal(t, int64(7), result[0].Key1.End)
	assert.Equal(t, int64(5), result[0].Key2.Start)
	assert.Equal(t, int64(8), result[0].Key2.End)
	assert.Equal(t, int64(0), result[0].MatchLen) // not provided
}

func TestParseLCSMatchedPositions_ValidWithMatchLen(t *testing.T) {
	input := []any{
		[]any{
			[]any{int64(2), int64(3)},
			[]any{int64(0), int64(1)},
			int64(2),
		},
	}
	result, err := ParseLCSMatchedPositions(input)
	assert.NoError(t, err)
	assert.Len(t, result, 1)
	assert.Equal(t, int64(2), result[0].MatchLen)
}

func TestParseLCSMatchedPositions_MultipleMatches(t *testing.T) {
	input := []any{
		[]any{
			[]any{int64(0), int64(1)},
			[]any{int64(2), int64(3)},
		},
		[]any{
			[]any{int64(4), int64(5)},
			[]any{int64(6), int64(7)},
			int64(2),
		},
	}
	result, err := ParseLCSMatchedPositions(input)
	assert.NoError(t, err)
	assert.Len(t, result, 2)
}

func TestParseLCSMatchedPositions_InvalidMatchShape(t *testing.T) {
	// Match with length 1 - invalid
	input := []any{
		[]any{int64(1)},
	}
	_, err := ParseLCSMatchedPositions(input)
	assert.Error(t, err)
}

func TestParseLCSMatchedPositions_InvalidKeyArray(t *testing.T) {
	// key1 has wrong length
	input := []any{
		[]any{
			[]any{int64(1)}, // only 1 element, expected 2
			[]any{int64(2), int64(3)},
		},
	}
	_, err := ParseLCSMatchedPositions(input)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "expected key1 to be an array of length 2")
}

func TestParseLCSMatchedPositions_NonNumericPositions(t *testing.T) {
	input := []any{
		[]any{
			[]any{"not_a_number", int64(1)},
			[]any{int64(2), int64(3)},
		},
	}
	_, err := ParseLCSMatchedPositions(input)
	assert.Error(t, err)
}

// --- CreateFieldInfoArray ---

func TestCreateFieldInfoArray_ValidPairs(t *testing.T) {
	input := []any{[]any{"field1", "value1", "field2", "value2"}}
	result := CreateFieldInfoArray(input)
	assert.Len(t, result, 2)
	assert.Equal(t, "field1", result[0].Field)
	assert.Equal(t, "value1", result[0].Value)
	assert.Equal(t, "field2", result[1].Field)
	assert.Equal(t, "value2", result[1].Value)
}

func TestCreateFieldInfoArray_EmptyInput(t *testing.T) {
	result := CreateFieldInfoArray([]any{})
	assert.Empty(t, result)
}

func TestCreateFieldInfoArray_NilInput(t *testing.T) {
	result := CreateFieldInfoArray(nil)
	assert.Empty(t, result) // should not panic
}

func TestCreateFieldInfoArray_NonArrayInput(t *testing.T) {
	result := CreateFieldInfoArray("not_an_array")
	assert.Empty(t, result) // should not panic
}

func TestCreateFieldInfoArray_OddNumberOfElements(t *testing.T) {
	// 3 elements = only 1 pair extracted, last element ignored
	input := []any{[]any{"field1", "value1", "field2"}}
	result := CreateFieldInfoArray(input)
	assert.Len(t, result, 1)
	assert.Equal(t, "field1", result[0].Field)
}

func TestCreateFieldInfoArray_NonStringElements(t *testing.T) {
	// Non-string field-value pairs should be skipped
	input := []any{[]any{int64(1), int64(2)}}
	result := CreateFieldInfoArray(input)
	assert.Empty(t, result) // skipped because not strings
}

// --- CreateStreamEntry ---

func TestCreateStreamEntry_ValidEntry(t *testing.T) {
	data := map[string]any{
		"first-entry": []any{
			"1234-0",
			[]any{"field1", "value1"},
		},
	}
	entry := CreateStreamEntry(data, "first-entry")
	assert.Equal(t, "1234-0", entry.ID)
	assert.Len(t, entry.Fields, 1)
	assert.Equal(t, "field1", entry.Fields[0].Field)
	assert.Equal(t, "value1", entry.Fields[0].Value)
}

func TestCreateStreamEntry_MissingKey(t *testing.T) {
	data := map[string]any{}
	entry := CreateStreamEntry(data, "nonexistent")
	assert.Equal(t, "", entry.ID) // zero value
	assert.Nil(t, entry.Fields)
}

func TestCreateStreamEntry_ShortArray(t *testing.T) {
	data := map[string]any{
		"entry": []any{"id-only"},
	}
	entry := CreateStreamEntry(data, "entry")
	assert.Equal(t, "", entry.ID) // not enough elements
}
