package common

import (
	"hash/crc32"

	"github.com/sqids/sqids-go"
)

const alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

type IdGenerator struct {
	sqids *sqids.Sqids
}

func NewSqidsIdGenerator() *IdGenerator {
	s, _ := sqids.New(sqids.Options{
		Alphabet: alphabet,
	})
	return &IdGenerator{sqids: s}
}

func (g *IdGenerator) Generate(data string) string {
	crc := crc32.ChecksumIEEE([]byte(data))
	id, _ := g.sqids.Encode([]uint64{uint64(crc)})
	return id
}
