package builder

import (
	"github.com/blackstorm/markdownbrain/common"
)

type DatabaseBuilder interface {
	Build(source string, db *common.DB) error
}
