// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// --- ConvertArrayOf ---

func TestConvertArrayOf_Strings(t *testing.T) {
	input := []any{"a", "b", "c"}
	result, err := ConvertArrayOf[string](input)
	assert.NoError(t, err)
	assert.Equal(t, []string{"a", "b", "c"}, result)
}

func TestConvertArrayOf_Int64(t *testing.T) {
	input := []any{int64(1), int64(2), int64(3)}
	result, err := ConvertArrayOf[int64](input)
	assert.NoError(t, err)
	assert.Equal(t, []int64{1, 2, 3}, result)
}

func TestConvertArrayOf_EmptyArray(t *testing.T) {
	input := []any{}
	result, err := ConvertArrayOf[string](input)
	assert.NoError(t, err)
	assert.Equal(t, []string{}, result)
}

func TestConvertArrayOf_TypeMismatch(t *testing.T) {
	input := []any{"a", int64(42)} // mixed types
	_, err := ConvertArrayOf[string](input)
	assert.Error(t, err)
}

// --- ConvertMapOf ---

func TestConvertMapOf_StringValues(t *testing.T) {
	input := map[string]any{"key1": "val1", "key2": "val2"}
	result, err := ConvertMapOf[string](input)
	assert.NoError(t, err)
	expected := map[string]string{"key1": "val1", "key2": "val2"}
	assert.Equal(t, expected, result)
}

func TestConvertMapOf_Float64Values(t *testing.T) {
	input := map[string]any{"score": float64(1.5)}
	result, err := ConvertMapOf[float64](input)
	assert.NoError(t, err)
	expected := map[string]float64{"score": 1.5}
	assert.Equal(t, expected, result)
}

func TestConvertMapOf_EmptyMap(t *testing.T) {
	input := map[string]any{}
	result, err := ConvertMapOf[string](input)
	assert.NoError(t, err)
	assert.Equal(t, map[string]string{}, result)
}

func TestConvertMapOf_TypeMismatch(t *testing.T) {
	input := map[string]any{"key": int64(42)}
	_, err := ConvertMapOf[string](input)
	assert.Error(t, err)
}

// --- ConvertArrayOfNilOr ---

func TestConvertArrayOfNilOr_AllValues(t *testing.T) {
	input := []any{"a", "b", "c"}
	result, err := ConvertArrayOfNilOr[string](input)
	assert.NoError(t, err)
	resultSlice := result.([]models.Result[string])
	assert.Len(t, resultSlice, 3)
	for i, v := range []string{"a", "b", "c"} {
		assert.False(t, resultSlice[i].IsNil())
		assert.Equal(t, v, resultSlice[i].Value())
	}
}

func TestConvertArrayOfNilOr_WithNils(t *testing.T) {
	input := []any{"a", nil, "c"}
	result, err := ConvertArrayOfNilOr[string](input)
	assert.NoError(t, err)
	resultSlice := result.([]models.Result[string])
	assert.Len(t, resultSlice, 3)
	assert.False(t, resultSlice[0].IsNil())
	assert.Equal(t, "a", resultSlice[0].Value())
	assert.True(t, resultSlice[1].IsNil())
	assert.False(t, resultSlice[2].IsNil())
	assert.Equal(t, "c", resultSlice[2].Value())
}

func TestConvertArrayOfNilOr_AllNils(t *testing.T) {
	input := []any{nil, nil, nil}
	result, err := ConvertArrayOfNilOr[string](input)
	assert.NoError(t, err)
	resultSlice := result.([]models.Result[string])
	assert.Len(t, resultSlice, 3)
	for _, r := range resultSlice {
		assert.True(t, r.IsNil())
	}
}

func TestConvertArrayOfNilOr_EmptyArray(t *testing.T) {
	input := []any{}
	result, err := ConvertArrayOfNilOr[string](input)
	assert.NoError(t, err)
	resultSlice := result.([]models.Result[string])
	assert.Empty(t, resultSlice)
}

func TestConvertArrayOfNilOr_TypeMismatch(t *testing.T) {
	input := []any{int64(42)}
	_, err := ConvertArrayOfNilOr[string](input)
	assert.Error(t, err)
}

// --- ConvertKeyWithMemberAndScore ---

func TestConvertKeyWithMemberAndScore_Valid(t *testing.T) {
	input := []any{"mykey", "member1", float64(1.5)}
	result, err := ConvertKeyWithMemberAndScore(input)
	assert.NoError(t, err)
	kms := result.(models.KeyWithMemberAndScore)
	assert.Equal(t, "mykey", kms.Key)
	assert.Equal(t, "member1", kms.Member)
	assert.Equal(t, float64(1.5), kms.Score)
}

func TestConvertKeyWithMemberAndScore_ZeroScore(t *testing.T) {
	input := []any{"key", "member", float64(0)}
	result, err := ConvertKeyWithMemberAndScore(input)
	assert.NoError(t, err)
	kms := result.(models.KeyWithMemberAndScore)
	assert.Equal(t, float64(0), kms.Score)
}

func TestConvertKeyWithMemberAndScore_NegativeScore(t *testing.T) {
	input := []any{"key", "member", float64(-99.5)}
	result, err := ConvertKeyWithMemberAndScore(input)
	assert.NoError(t, err)
	kms := result.(models.KeyWithMemberAndScore)
	assert.Equal(t, float64(-99.5), kms.Score)
}

// --- MakeConvertMapOfMemberAndScore ---

func TestMakeConvertMapOfMemberAndScore_Ascending(t *testing.T) {
	input := map[string]any{"b": float64(2.0), "a": float64(1.0), "c": float64(3.0)}
	fn := MakeConvertMapOfMemberAndScore(false)
	result, err := fn(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Len(t, mas, 3)
	// Should be sorted ascending by score
	assert.Equal(t, float64(1.0), mas[0].Score)
	assert.Equal(t, float64(2.0), mas[1].Score)
	assert.Equal(t, float64(3.0), mas[2].Score)
}

func TestMakeConvertMapOfMemberAndScore_Descending(t *testing.T) {
	input := map[string]any{"b": float64(2.0), "a": float64(1.0), "c": float64(3.0)}
	fn := MakeConvertMapOfMemberAndScore(true)
	result, err := fn(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Len(t, mas, 3)
	// Should be sorted descending by score
	assert.Equal(t, float64(3.0), mas[0].Score)
	assert.Equal(t, float64(2.0), mas[1].Score)
	assert.Equal(t, float64(1.0), mas[2].Score)
}

func TestMakeConvertMapOfMemberAndScore_EqualScores_SortByMember(t *testing.T) {
	input := map[string]any{"b": float64(1.0), "a": float64(1.0)}
	fn := MakeConvertMapOfMemberAndScore(false)
	result, err := fn(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Len(t, mas, 2)
	// Same score, sorted alphabetically ascending by member
	assert.Equal(t, "a", mas[0].Member)
	assert.Equal(t, "b", mas[1].Member)
}

func TestMakeConvertMapOfMemberAndScore_EmptyMap(t *testing.T) {
	input := map[string]any{}
	fn := MakeConvertMapOfMemberAndScore(false)
	result, err := fn(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Empty(t, mas)
}

// --- ConvertKeyWithArrayOfMembersAndScores ---

func TestConvertKeyWithArrayOfMembersAndScores_Valid(t *testing.T) {
	input := []any{
		"mykey",
		map[string]any{"member1": float64(1.0), "member2": float64(2.0)},
	}
	result, err := ConvertKeyWithArrayOfMembersAndScores(input)
	assert.NoError(t, err)
	kwams := result.(models.Result[models.KeyWithArrayOfMembersAndScores])
	assert.False(t, kwams.IsNil())
	assert.Equal(t, "mykey", kwams.Value().Key)
	assert.Len(t, kwams.Value().MembersAndScores, 2)
}

func TestConvertKeyWithArrayOfMembersAndScores_Nil(t *testing.T) {
	result, err := ConvertKeyWithArrayOfMembersAndScores(nil)
	assert.NoError(t, err)
	assert.Nil(t, result)
}

// --- Convert2DArrayOfString ---

func TestConvert2DArrayOfString_Valid(t *testing.T) {
	input := []any{
		[]any{"a", "b"},
		[]any{"c", "d", "e"},
	}
	result, err := Convert2DArrayOfString(input)
	assert.NoError(t, err)
	expected := [][]string{{"a", "b"}, {"c", "d", "e"}}
	assert.Equal(t, expected, result)
}

func TestConvert2DArrayOfString_Empty(t *testing.T) {
	input := []any{}
	result, err := Convert2DArrayOfString(input)
	assert.NoError(t, err)
	assert.Equal(t, [][]string{}, result)
}

func TestConvert2DArrayOfString_InnerEmpty(t *testing.T) {
	input := []any{[]any{}, []any{"a"}}
	result, err := Convert2DArrayOfString(input)
	assert.NoError(t, err)
	expected := [][]string{{}, {"a"}}
	assert.Equal(t, expected, result)
}

// --- Convert2DArrayOfFloat ---

func TestConvert2DArrayOfFloat_Valid(t *testing.T) {
	input := []any{
		[]any{float64(1.1), float64(2.2)},
		[]any{float64(3.3)},
	}
	result, err := Convert2DArrayOfFloat(input)
	assert.NoError(t, err)
	expected := [][]float64{{1.1, 2.2}, {3.3}}
	assert.Equal(t, expected, result)
}

func TestConvert2DArrayOfFloat_WithNilInner(t *testing.T) {
	// GeoPos returns nil for non-existing keys
	input := []any{nil, []any{float64(1.0), float64(2.0)}}
	result, err := Convert2DArrayOfFloat(input)
	assert.NoError(t, err)
	resultSlice := result.([][]float64)
	assert.Len(t, resultSlice, 2)
	assert.Nil(t, resultSlice[0])
	assert.Equal(t, []float64{1.0, 2.0}, resultSlice[1])
}

// --- ConvertXAutoClaimResponse ---

func TestConvertXAutoClaimResponse_WithDeletedMessages(t *testing.T) {
	input := []any{
		"next-id",
		map[string]any{"1-0": []any{"field1", "value1"}},
		[]any{"deleted-1", "deleted-2"},
	}
	result, err := ConvertXAutoClaimResponse(input)
	assert.NoError(t, err)
	resp := result.(models.XAutoClaimResponse)
	assert.Equal(t, "next-id", resp.NextEntry)
	assert.Len(t, resp.ClaimedEntries, 1)
	assert.Equal(t, []string{"deleted-1", "deleted-2"}, resp.DeletedMessages)
}

func TestConvertXAutoClaimResponse_WithoutDeletedMessages(t *testing.T) {
	input := []any{
		"next-id",
		map[string]any{},
	}
	result, err := ConvertXAutoClaimResponse(input)
	assert.NoError(t, err)
	resp := result.(models.XAutoClaimResponse)
	assert.Equal(t, "next-id", resp.NextEntry)
	assert.Empty(t, resp.ClaimedEntries)
	assert.Nil(t, resp.DeletedMessages)
}

func TestConvertXAutoClaimResponse_InvalidLength(t *testing.T) {
	input := []any{"only-one"}
	_, err := ConvertXAutoClaimResponse(input)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "unexpected response array length")
}

// --- ConvertXAutoClaimJustIdResponse ---

func TestConvertXAutoClaimJustIdResponse_Valid(t *testing.T) {
	input := []any{
		"next-id",
		[]any{"id-1", "id-2"},
		[]any{"deleted-1"},
	}
	result, err := ConvertXAutoClaimJustIdResponse(input)
	assert.NoError(t, err)
	resp := result.(models.XAutoClaimJustIdResponse)
	assert.Equal(t, "next-id", resp.NextEntry)
	assert.Equal(t, []string{"id-1", "id-2"}, resp.ClaimedEntries)
	assert.Equal(t, []string{"deleted-1"}, resp.DeletedMessages)
}

func TestConvertXAutoClaimJustIdResponse_WithoutDeleted(t *testing.T) {
	input := []any{
		"next-id",
		[]any{"id-1"},
	}
	result, err := ConvertXAutoClaimJustIdResponse(input)
	assert.NoError(t, err)
	resp := result.(models.XAutoClaimJustIdResponse)
	assert.Nil(t, resp.DeletedMessages)
}

func TestConvertXAutoClaimJustIdResponse_InvalidLength(t *testing.T) {
	input := []any{"only-one"}
	_, err := ConvertXAutoClaimJustIdResponse(input)
	assert.Error(t, err)
}

// --- ConvertXPendingResponse ---

func TestConvertXPendingResponse_WithConsumers(t *testing.T) {
	input := []any{
		int64(5),        // NumOfMessages
		"1-0",           // StartId
		"5-0",           // EndId
		[]any{           // ConsumerMessages
			[]any{"consumer1", "3"},
			[]any{"consumer2", "2"},
		},
	}
	result, err := ConvertXPendingResponse(input)
	assert.NoError(t, err)
	summary := result.(models.XPendingSummary)
	assert.Equal(t, int64(5), summary.NumOfMessages)
	assert.False(t, summary.StartId.IsNil())
	assert.Equal(t, "1-0", summary.StartId.Value())
	assert.False(t, summary.EndId.IsNil())
	assert.Equal(t, "5-0", summary.EndId.Value())
	assert.Len(t, summary.ConsumerMessages, 2)
	assert.Equal(t, "consumer1", summary.ConsumerMessages[0].ConsumerName)
	assert.Equal(t, int64(3), summary.ConsumerMessages[0].MessageCount)
}

func TestConvertXPendingResponse_NilStartEnd(t *testing.T) {
	input := []any{
		int64(0),
		nil,
		nil,
		[]any{},
	}
	result, err := ConvertXPendingResponse(input)
	assert.NoError(t, err)
	summary := result.(models.XPendingSummary)
	assert.Equal(t, int64(0), summary.NumOfMessages)
	assert.True(t, summary.StartId.IsNil())
	assert.True(t, summary.EndId.IsNil())
	assert.Empty(t, summary.ConsumerMessages)
}

// --- ConvertXPendingWithOptionsResponse ---

func TestConvertXPendingWithOptionsResponse_Valid(t *testing.T) {
	input := []any{
		[]any{"msg-1", "consumer1", int64(1000), int64(3)},
		[]any{"msg-2", "consumer2", int64(2000), int64(1)},
	}
	result, err := ConvertXPendingWithOptionsResponse(input)
	assert.NoError(t, err)
	details := result.([]models.XPendingDetail)
	assert.Len(t, details, 2)
	assert.Equal(t, "msg-1", details[0].Id)
	assert.Equal(t, "consumer1", details[0].ConsumerName)
	assert.Equal(t, int64(1000), details[0].IdleTime)
	assert.Equal(t, int64(3), details[0].DeliveryCount)
}

func TestConvertXPendingWithOptionsResponse_Empty(t *testing.T) {
	input := []any{}
	result, err := ConvertXPendingWithOptionsResponse(input)
	assert.NoError(t, err)
	details := result.([]models.XPendingDetail)
	assert.Empty(t, details)
}

// --- ConvertScanResult ---

func TestConvertScanResult_Valid(t *testing.T) {
	input := []any{
		"42",
		[]any{"key1", "key2", "key3"},
	}
	result, err := ConvertScanResult(input)
	assert.NoError(t, err)
	scanResult := result.(models.ScanResult)
	assert.Equal(t, []string{"key1", "key2", "key3"}, scanResult.Data)
}

func TestConvertScanResult_EmptyData(t *testing.T) {
	input := []any{
		"0",
		[]any{},
	}
	result, err := ConvertScanResult(input)
	assert.NoError(t, err)
	scanResult := result.(models.ScanResult)
	assert.Empty(t, scanResult.Data)
}

// --- ConvertArrayOfMemberAndScore ---

func TestConvertArrayOfMemberAndScore_Valid(t *testing.T) {
	input := []any{
		[]any{"member1", float64(1.5)},
		[]any{"member2", float64(2.5)},
	}
	result, err := ConvertArrayOfMemberAndScore(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Len(t, mas, 2)
	assert.Equal(t, "member1", mas[0].Member)
	assert.Equal(t, float64(1.5), mas[0].Score)
	assert.Equal(t, "member2", mas[1].Member)
	assert.Equal(t, float64(2.5), mas[1].Score)
}

func TestConvertArrayOfMemberAndScore_Empty(t *testing.T) {
	input := []any{}
	result, err := ConvertArrayOfMemberAndScore(input)
	assert.NoError(t, err)
	mas := result.([]models.MemberAndScore)
	assert.Empty(t, mas)
}
